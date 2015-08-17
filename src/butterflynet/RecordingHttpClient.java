package butterflynet;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultManagedHttpClientConnection;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.archive.util.Recorder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Constructs an Apache HttpClient instance which records the raw HTTP request and response.
 */
final class RecordingHttpClient {

    private RecordingHttpClient() {}

    /**
     * Creates a new HttpClient with default settings that will be recorded by the given recorder.
     */
    public static CloseableHttpClient create(Recorder recorder) {
        return HttpClientBuilder.create()
                .setConnectionManager(createConnectionManager(recorder))
                .build();
    }


    /**
     * Same as BasicHttpClientConnectionManager.getDefaultRegistry().
     */
    private static Registry<ConnectionSocketFactory> createRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
    }

    /**
     * Creates a connection manager that will register each created connection for recording with the given
     * recorder.
     */
    public static HttpClientConnectionManager createConnectionManager(Recorder recorder) {
        return new BasicHttpClientConnectionManager(createRegistry(), new ConnectionFactory(recorder));
    }

    private static class ConnectionFactory extends ManagedHttpClientConnectionFactory {
        final AtomicLong COUNTER = new AtomicLong();
        final Recorder recorder;

        ConnectionFactory(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public ManagedHttpClientConnection create(HttpRoute route, ConnectionConfig config) {
            if (config == null) {
                config = ConnectionConfig.DEFAULT;
            }
            return new Connection(recorder, "recording-http-client-" + COUNTER.getAndIncrement(), config.getBufferSize());
        }
    }

    /**
     * Wraps the HTTP connection's input and output streams so they can be recorded.
     */
    private static class Connection extends DefaultManagedHttpClientConnection {
        final Recorder recorder;

        Connection(Recorder recorder, String id, int buffersize) {
            super(id, buffersize);
            this.recorder = recorder;
        }

        @Override
        protected InputStream getSocketInputStream(Socket socket) throws IOException {
            return recorder.inputWrap(super.getSocketInputStream(socket));
        }

        @Override
        protected OutputStream getSocketOutputStream(Socket socket) throws IOException {
            return recorder.outputWrap(super.getSocketOutputStream(socket));
        }

        @Override
        public void close() throws IOException {
            super.close();
            recorder.close();
            recorder.closeRecorders();
        }
    }
}
