package butterflynet;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Key;
import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import droute.*;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.*;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static droute.Response.*;
import static droute.Route.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Webapp implements Handler {
    static final NetHttpTransport HTTP = new NetHttpTransport();
    static final JsonFactory JSON = new GsonFactory();

    final Configuration fremarkerConfig;
    final Handler routes = routes(
            resources("/webjars", "META-INF/resources/webjars"),
            resources("/assets", "butterflynet/assets"),
            GET("/", this::home),
            POST("/", this::submit),
            GET("/events", this::events),
            GET("/login", this::login),
            GET("/authcb", this::authcb),
            notFoundHandler("404. Alas, there is nothing here."));

    final Handler handler;
    final Butterflynet butterflynet = new Butterflynet();
    final AuthorizationCodeFlow authFlow;

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

        Config config = butterflynet.config;
        authFlow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                HTTP, JSON,
                new GenericUrl(config.getOAuthServer() + "/token"),
                new BasicAuthentication(config.getOAuthClientId(), config.getOAuthClientSecret()),
                config.getOAuthClientId(),
                config.getOAuthServer() + "/authorize").build();

        butterflynet.startWorker();
    }

    public static class UserInfo {
        @Key
        public String sub;

        @Key("preferred_username")
        public String preferredUsername;

        @Key("given_name")
        public String givenName;

        @Key("family_name")
        public String familyName;

        @Key
        public String email;

        @Key
        public String name;

        @Override
        public String toString() {
            return "UserInfo{" +
                    "sub='" + sub + '\'' +
                    ", preferredUsername='" + preferredUsername + '\'' +
                    ", givenName='" + givenName + '\'' +
                    ", familyName='" + familyName + '\'' +
                    ", email='" + email + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    Response login(Request request) {
        return temporaryRedirect(authFlow.newAuthorizationUrl().build());
    }

    Response authcb(Request request) {
        String code = request.queryParam("code");
        try {
            TokenResponse tokenResponse = authFlow.newTokenRequest(code).execute();
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(tokenResponse.getAccessToken());
            GenericUrl url = new GenericUrl(butterflynet.config.getOAuthServer() + "/userinfo");
            HttpResponse response = HTTP.createRequestFactory(credential)
                    .buildGetRequest(url)
                    .setParser(JSON.createJsonObjectParser())
                    .execute();
            UserInfo userInfo = response.parseAs(UserInfo.class);
            return response("wtf " + userInfo);
        } catch (IOException e) {
            e.printStackTrace();
            return response(500, e.toString());
        }
    }

    Response home(Request request) {
        try (Db db = butterflynet.dbPool.take()) {
            List<CaptureProgress> captureProgressList = new ArrayList();
            for (Db.Capture capture : db.recentCaptures()) {
                captureProgressList.add(new CaptureProgress(capture, butterflynet.getProgress(capture.id)));
            }
            return render("home.ftl",
                    "csrfToken", Csrf.token(request),
                    "captureProgressList", captureProgressList);
        }
    }

    Response events(Request request) {
        return response((Streamable)(OutputStream out) -> {
            Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateSerializer()).create();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
            Template template = fremarkerConfig.getTemplate("capture-list.ftl");
            while (true) {
                List<CaptureProgress> captureProgressList = new ArrayList();
                try (Db db = butterflynet.dbPool.take()) {
                    for (Db.Capture capture : db.recentCaptures()) {
                        captureProgressList.add(new CaptureProgress(capture, butterflynet.getProgress(capture.id)));
                    }
                }

                Map<String,Object> model = new HashMap<>();
                model.put("captureProgressList", captureProgressList);
                StringWriter buf = new StringWriter();
                try {
                    template.process(model, buf);
                } catch (TemplateException e) {
                    throw new RuntimeException(e);
                }
                bw.write("event: update\r\ndata: ");
                JsonWriter jw = new JsonWriter(bw);
                jw.beginObject();
                jw.name("captureList");
                jw.value(buf.toString());
                jw.endObject();
                bw.write("\r\n\r\n\r\n");
                jw.flush();
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
        public final Db.Capture capture;
        public final long length;
        public final long position;
        public final double percentage;

        public CaptureProgress(Db.Capture capture, HttpArchiver.Progress progress) {
            this.capture = capture;
            if (progress != null) {
                length = progress.length();
                position = progress.position();
                percentage = 100.0 * position / length;
            } else {
                length = 0;
                position = 0;
                percentage = 50;
            }
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
