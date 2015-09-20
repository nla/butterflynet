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

public class Webapp implements Handler {
    final Configuration fremarkerConfig;
    final Handler routes = routes(
            resources("/webjars", "META-INF/resources/webjars"),
            resources("/assets", "butterflynet/assets"),
            resources("/tags", "butterflynet/tags"),
            GET("/", this::home),
            POST("/", this::submit),
            GET("/events", this::events),
            notFoundHandler("404. Alas, there is nothing here."));

    final Handler handler;
    final Butterflynet butterflynet = new Butterflynet();
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
        Runtime.getRuntime().addShutdownHook(new Thread(butterflynet::close));
        fremarkerConfig = FreeMarkerHandler.defaultConfiguration(Webapp.class, "/butterflynet/views");
        fremarkerConfig.addAutoInclude("layout.ftl");
        BeansWrapper beansWrapper = BeansWrapper.getDefaultInstance();
        beansWrapper.setExposeFields(true);
        fremarkerConfig.setObjectWrapper(beansWrapper);
        Handler handler = new FreeMarkerHandler(fremarkerConfig, routes);
        handler = Csrf.protect(handler);
        this.handler = errorHandler(handler);
        butterflynet.startWorker();
    }

    Response home(Request request) {
        try (Db db = butterflynet.dbPool.take()) {
            String tableHtml = tag.renderJson("{\"captures\":" + buildProgressList(db) + "}");
            return render("home.ftl",
                    "csrfToken", Csrf.token(request),
                    "tableHtml", tableHtml);
        }
    }

    private String buildProgressList(Db db) {
        List<CaptureProgress> captureProgressList = new ArrayList();
        for (Db.Capture capture : db.recentCaptures()) {
            captureProgressList.add(new CaptureProgress(butterflynet.config.getReplayUrl(),
                    capture, butterflynet.getProgress(capture.id)));
        }
        return gson.toJson(captureProgressList);
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

        public final String originalUrl;
        public final String archiveUrl;
        public final Date started;
        public final String length;
        public final String position;
        public final double percentage;
        public final String state;

        public CaptureProgress(String replayUrl, Db.Capture capture, HttpArchiver.Progress progress) {
            originalUrl = capture.url;
            if (replayUrl.isEmpty()) {
                archiveUrl = originalUrl;
            } else {
                String timestamp = capture.archived.toInstant().atOffset(ZoneOffset.UTC).format(WAYBACK_DATE_FORMAT);
                archiveUrl = replayUrl + timestamp + "/" + originalUrl;
            }
            started = capture.started;
            state = capture.getStateName();

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
        butterflynet.submit(request.formParam("url"));
        return seeOther(request.contextUri().toString());
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
}
