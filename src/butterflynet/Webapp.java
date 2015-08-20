package butterflynet;

import droute.*;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;

import java.io.PrintWriter;
import java.io.StringWriter;

import static droute.Response.*;
import static droute.Route.*;

public class Webapp implements Handler {

    final Handler routes = routes(
            resources("/webjars", "META-INF/resources/webjars"),
            resources("/assets", "butterflynet/assets"),
            GET("/", this::home),
            POST("/", this::submit),
            notFoundHandler("404. Alas, there is nothing here."));

    final Handler handler;
    final Butterflynet butterflynet = new Butterflynet();

    public Webapp() {
        Configuration fremarkerConfig = FreeMarkerHandler.defaultConfiguration(Webapp.class, "/butterflynet/views");
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
            return render("home.ftl",
                    "csrfToken", Csrf.token(request),
                    "captures", db.recentCaptures());
        }
    }

    Response submit(Request request) {
        butterflynet.submit(request.formParam("url"));

        return home(request);
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
