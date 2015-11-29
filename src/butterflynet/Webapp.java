package butterflynet;

import com.google.gson.*;
import droute.*;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import riothorn.Riothorn;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static droute.Response.*;
import static droute.Route.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class Webapp implements Handler, AutoCloseable {
    final static long SESSION_EXPIRY_MILLIS = HOURS.toMillis(12);

    final Configuration fremarkerConfig;
    final Handler routes = routes(
            resources("/webjars", "META-INF/resources/webjars"),
            resources("/assets", "butterflynet/assets"),
            resources("/tags", "butterflynet/tags"),
            GET("/", this::home),
            POST("/", this::submit),
            GET("/events", this::events),
            GET("/login", this::login),
            GET("/authcb", this::authcb),
            POST("/cancel", this::cancel),
            GET("/settings", this::settings),
            POST("/settings/allowed-media-types/create", this::createAllowedMediaType),
            POST("/settings/allowed-media-types/delete", this::deleteAllowedMediaType),
            notFoundHandler("404. Alas, there is nothing here."));

    final Handler handler;
    final Butterflynet butterflynet;
    final OAuth oauth;
    final Riothorn riothorn = new Riothorn();
    final Riothorn.Tag tag = riothorn.compile(slurpResource("/butterflynet/tags/capture-list.tag"));
    final Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateSerializer()).create();

    String slurpResource(String path) {
        try (Reader r = new InputStreamReader(Webapp.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            for (;;) {
                int n = r.read(buf);
                if (n < 0) {
                    break;
                }
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("unable to read resource " + path, e);
        }
    }

    public Webapp() {
        this(new Config());
        butterflynet.startWorker();
    }

    public Webapp(Config config) {
        butterflynet = new Butterflynet(config);

        if (config.getOAuthServer() != null) {
            oauth = new OAuth(config.getOAuthServer(), config.getOAuthClientId(), config.getOAuthClientSecret());
        } else {
            oauth = null;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(butterflynet::close));
        fremarkerConfig = FreeMarkerHandler.defaultConfiguration(Webapp.class, "/butterflynet/views");
        fremarkerConfig.addAutoInclude("layout.ftl");
        BeansWrapper beansWrapper = BeansWrapper.getDefaultInstance();
        beansWrapper.setExposeFields(true);
        fremarkerConfig.setObjectWrapper(beansWrapper);
        Handler handler = new FreeMarkerHandler(fremarkerConfig, routes);
        handler = Csrf.protect(handler);
        this.handler = errorHandler(handler);
    }

    Response login(Request request) {
        if (oauth != null) {
            return temporaryRedirect(oauth.authUrl(Csrf.token(request)));
        } else {
            return createSession(request, new UserInfo(0, "anonymous", "anonymous", "anonymous", "Anonymous", "anonymous@example.org"));
        }
    }

    Response authcb(Request request) {
        UserInfo userInfo = oauth.authCallback(Csrf.token(request),
                request.queryParam("code"),
                request.queryParam("state"));
        return createSession(request, userInfo);
    }

    private Response createSession(Request request, UserInfo userInfo) {
        String sessionId = createSession(userInfo, SESSION_EXPIRY_MILLIS);
        return Cookies.set(seeOther(request.contextPath()),
                "id", sessionId,
                Cookies.autosecure(request),
                Cookies.httpOnly(),
                Cookies.path(request.contextPath()),
                Cookies.maxAge(MILLISECONDS.toSeconds(SESSION_EXPIRY_MILLIS)));
    }

    String createSession(UserInfo userInfo, long duration) {
        try (Db db = butterflynet.dbPool.take()) {
            String sessionId = Tokens.generate();
            long userId = db.upsertUser(userInfo.username, userInfo.issuer, userInfo.subject, userInfo.name, userInfo.email);
            long expiry = System.currentTimeMillis() + duration;
            db.insertSession(sessionId, userId, expiry);
            return sessionId;
        }
    }

    UserInfo currentUser(Db db, Request request) {
        String sessionId = Cookies.get(request, "id");
        if (sessionId != null) {
            db.expireSessions(System.currentTimeMillis());
            UserInfo user = db.findUserBySessionId(sessionId);
            if (user != null) {
                return user;
            }
        }
        throw new LoginRequired();
    }

    Response home(Request request) {
        UserInfo user = null;

        try (Db db = butterflynet.dbPool.take()) {
            String tableHtml = tag.renderJson("{\"captures\":" + buildProgressList(db) + "}");
            return render("home.ftl",
                    "csrfToken", Csrf.token(request),
                    "tableHtml", tableHtml,
                    "user", currentUser(db, request));
        }
    }

    private String buildProgressList(Db db) {
        List<CaptureProgress> captureProgressList = new ArrayList();
        for (Db.CaptureDetailed capture : db.recentCaptures()) {
            captureProgressList.add(new CaptureProgress(butterflynet.config.getReplayUrl(),
                    capture, butterflynet.getProgress(capture.id)));
        }
        return gson.toJson(captureProgressList);
    }

    Response settings(Request request) {
        try (Db db = butterflynet.dbPool.take()) {
            return render("settings.ftl",
                    "csrfToken", Csrf.token(request),
                    "allowedMediaTypes", db.listAllowedMediaTypes());
        }
    }

    Response events(Request request) {
        return response((Streamable)(OutputStream out) -> {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
            while (true) {
                String json;
                try (Db db = butterflynet.dbPool.take()) {
                    json = buildProgressList(db);
                }
                bw.write("event: update\r\ndata: {\"captures\":");
                bw.write(json);
                bw.write("}\r\n\r\n\r\n");
                bw.flush();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).withHeader("Content-Type", "text/event-stream");
    }

    public void close() {
        butterflynet.close();
    }

    private static class DateSerializer implements JsonSerializer<Date> {
        DateFormat dateFormat;

        DateSerializer() {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        @Override
        public synchronized JsonElement serialize(Date date, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(dateFormat.format(date));
        }
    }

    public static class CaptureProgress {
        static final DateTimeFormatter WAYBACK_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US);

        public final long id;
        public final String originalUrl;
        public final String archiveUrl;
        public final String calendarUrl;
        public final Date started;
        public final String length;
        public final String position;
        public final double percentage;
        public final String state;
        public final String reason;
        public final String username;
        public final long userId;

        public CaptureProgress(String replayUrl, Db.CaptureDetailed capture, HttpArchiver.Progress progress) {
            id = capture.id;
            originalUrl = capture.url;
            if (replayUrl.isEmpty()) {
                archiveUrl = originalUrl;
                calendarUrl = originalUrl;
            } else {
                String timestamp = capture.archived.toInstant().atOffset(ZoneOffset.UTC).format(WAYBACK_DATE_FORMAT);
                archiveUrl = replayUrl + timestamp + "/" + originalUrl;
                calendarUrl = replayUrl + "*" + "/" + originalUrl;
            }
            started = capture.started;
            state = capture.getStateName();
            reason = capture.reason;
            userId = capture.userId;
            username = capture.username;

            long position = 0;
            long length = 0;

            if (capture.state == Db.ARCHIVED) {
                percentage = 100.0;
                length = capture.size;
                position = length;
            } else if (progress != null) {
                length = progress.length();
                position = progress.position();
                percentage = 100.0 * position / length;
            } else {
                percentage = 0.0;
            }

            this.length = si(length);
            this.position = si(position);
        }

        String si(long bytes) {
            if (bytes < 1000) {
                return bytes + " B";
            }
            int order = (int) (Math.log(bytes) / Math.log(1000));
            char unit = "kMGTPE".charAt(order - 1);
            return String.format("%.1f %sB", bytes / Math.pow(1000, order), unit);
        }
    }

    Response submit(Request request) {
        UserInfo user;
        try (Db db = butterflynet.dbPool.take()) {
            user = currentUser(db, request);
        }
        butterflynet.submit(request.formParam("url"), user.id);
        return seeOther(request.contextUri().toString());
    }

    Response cancel(Request request) {
        long id = Long.parseLong(request.formParam("id"));
        butterflynet.cancel(id);
        return seeOther(request.contextUri().toString());
    }

    Response createAllowedMediaType(Request request) {
        try (Db db = butterflynet.dbPool.take()) {
            currentUser(db, request);
            db.insertAllowedMediaType(request.formParam("mediaType").trim());
        }
        return seeOther(request.contextUri().resolve("settings").toString());
    }

    Response deleteAllowedMediaType(Request request) {
        try (Db db = butterflynet.dbPool.take()) {
            currentUser(db, request);
            db.deleteAllowedMediaType(request.formParam("mediaType").trim());
        }
        return seeOther(request.contextUri().resolve("settings").toString());
    }


    /**
     * Dump a copy of the stack trace to the client on uncaught exceptions.
     */
    static Handler errorHandler(Handler handler) {
        return request -> {
            try {
                return handler.handle(request);
            } catch (NotFound e) {
                return notFound(e.getMessage());
            } catch (LoginRequired e) {
                return seeOther(request.contextUri().resolve("login").toString());
            } catch (Throwable t) {
                StringWriter out = new StringWriter();
                t.printStackTrace();
                t.printStackTrace(new PrintWriter(out));
                return response(500, "Internal Server Error\n\n" + out.toString());
            }
        };
    }

    @Override
    public Response handle(Request request) {
        return handler.handle(request);
    }

    static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    static class LoginRequired extends RuntimeException {
    }
}
