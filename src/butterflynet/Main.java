package butterflynet;

import droute.Handler;
import droute.ShotgunHandler;
import droute.nanohttpd.NanoServer;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;

public class Main {
    public static void usage() {
        System.err.println("Usage: java " + Main.class.getName() + " [-b bindaddr] [-p port] [-i]");
        System.err.println("");
        System.err.println("  -b bindaddr    Bind to a particular IP address");
        System.err.println("  -i             Inherit the server socket via STDIN (for use with systemd, inetd etc)");
        System.err.println("  -p port        Local port to listen on");
        System.err.println("  -v, -vv, -vvv  Increase logging verbosity (info, debug, trace)");
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String host = null;
        boolean inheritSocket = false;
        String logLevel = "warn";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-b":
                    host = args[++i];
                    break;
                case "-i":
                    inheritSocket = true;
                    break;
                case "-v":
                    logLevel = "info";
                    break;
                case "-vv":
                    logLevel = "debug";
                    break;
                case "-vvv":
                    logLevel = "trace";
                    break;
                default:
                    usage();
                    System.exit(1);
            }
        }
        configureLogging(logLevel);
        runWebServer(port, host, inheritSocket);
    }

    private static void configureLogging(String logLevel) {
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel);
        }
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static void runWebServer(int port, String host, boolean inheritSocket) throws IOException {
        Handler handler = new ShotgunHandler("butterflynet.Webapp");
        if (inheritSocket) {
            Channel channel = System.inheritedChannel();
            if (channel != null && channel instanceof ServerSocketChannel) {
                new NanoServer(handler, ((ServerSocketChannel) channel).socket()).startAndJoin();
                System.exit(0);
            }
            System.err.println("When -i is given STDIN must be a ServerSocketChannel, but got " + channel);
            System.exit(1);
        }
        if (host != null) {
            new NanoServer(handler, host, port).startAndJoin();
        } else {
            new NanoServer(handler, port).startAndJoin();
        }
    }
}
