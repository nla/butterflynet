package butterflynet;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.archive.format.warc.WARCConstants;
import org.archive.io.RecordingInputStream;
import org.archive.io.RecordingOutputStream;
import org.archive.io.warc.*;
import org.archive.uid.UUIDGenerator;
import org.archive.util.ArchiveUtils;
import org.archive.util.Recorder;
import org.archive.util.anvl.ANVLRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static org.apache.http.HttpVersion.HTTP_1_0;
import static org.archive.format.warc.WARCConstants.*;

public class HttpArchiver implements AutoCloseable {
    final Logger log = LoggerFactory.getLogger(HttpArchiver.class);
    final static UUIDGenerator uuid = new UUIDGenerator();
    final int MAX_ACTIVE_WARCS = 2;
    final int MAX_WAIT_MS = 1000;
    final WARCWriterPool warcPool;

    public HttpArchiver(String warcPrefix, File outputDir) {
        WARCWriterPoolSettings warcSettings = new WARCWriterPoolSettingsData(
                warcPrefix,
                "${prefix}-${timestamp17}-${serialno}-" + getPid() + "~" + getHostName() + "~0",
                WARCConstants.DEFAULT_MAX_WARC_FILE_SIZE,
                true, // compress
                Arrays.asList(outputDir),
                Collections.emptyList(), // metadata
                new UUIDGenerator());
        warcPool = new WARCWriterPool(warcSettings, MAX_ACTIVE_WARCS, MAX_WAIT_MS);
    }

    /**
     * Hack to get our PID until the new process API is available in Java 9.
     */
    static String getPid() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
    }

    static String getHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    public void close() {
        warcPool.close();
    }

    static class Result {
        Date timestamp;
        int status;
        String reason;
        long size;
    }

    interface ProgressTracker {
        void register(Progress progress);
    }

    interface Progress {
        long length();
        long position();
    }

    Result archive(String url, ProgressTracker tracker, Collection<String> allowedMediaTypes) throws IOException, InterruptedException {
        Result result = new Result();

        /*
         * Use HTTP 1.0 and also add a "Connection: close" header to try to avoid chunked encoding.  We aren't
         * going to benefit much from keepalive and it's likely some simple WARC tools would be confused by it.
         */
        HttpGet request = new HttpGet(url);
        request.setProtocolVersion(HTTP_1_0);
        request.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);

        Recorder recorder = new Recorder(new File(System.getProperty("java.io.tmpdir")), "butterflynet-http-");
        Date date = new Date();
        String timestamp = ArchiveUtils.getLog14Date(date);
        try (CloseableHttpClient client = RecordingHttpClient.create(recorder);
             CloseableHttpResponse response = client.execute(request)) {
            long contentLength = parseContentLength(response);
            if (tracker != null) {
                progressHack(tracker, contentLength, recorder.getRecordedInput());
            }

            String contentType = parseContentType(response);
            if (!allowedMediaTypes.contains(contentType)) {
                throw new RuntimeException("File format '" + contentType + "' is not on the allowed list. Make sure the URL you're archiving is a PDF or Office document. If you need to archive a full web page or a new file format please contact Web Archiving for assistance.");
            }

            recorder.getRecordedInput().readToEndOfContent(contentLength);
            recorder.close();
            recorder.closeRecorders();

            result.timestamp = date;
            result.status = response.getStatusLine().getStatusCode();
            result.reason = response.getStatusLine().getReasonPhrase();
            result.size = recorder.getResponseContentLength();
        }
        WARCWriter warc = (WARCWriter) warcPool.borrowFile();
        try {
            warc.checkSize();
            URI responseId = writeResponse(warc, url, timestamp, recorder);
            writeRequest(warc, url, timestamp, recorder, responseId);
            warcPool.flush(); // reduce chance of a half-written record existing on disk at any given time
        } finally {
            warcPool.returnFile(warc);
        }
        return result;
    }


    /**
     * Unfortunately RecordingInputStream doesn't expose progress details.  For now lets hack around it by accessing
     * its private fields using reflection.
     */
    private void progressHack(ProgressTracker tracker, final long contentLength, final RecordingInputStream ris) {
        try {
            Field rosField = RecordingInputStream.class.getDeclaredField("recordingOutputStream");
            rosField.setAccessible(true);
            RecordingOutputStream ros = (RecordingOutputStream) rosField.get(ris);
            Field positionField = RecordingOutputStream.class.getDeclaredField("position");
            positionField.setAccessible(true);
            tracker.register(new Progress() {
                @Override
                public long length() {
                    return contentLength;
                }

                @Override
                public long position() {
                    try {
                        return positionField.getLong(ros) - ris.getContentBegin();
                    } catch (IllegalAccessException e) {
                        return 0; // give up
                    }
                }
            });
        } catch (IllegalAccessException | NoSuchFieldException e) {
            log.warn("Unable to access download progress", e);
        }
    }

    static URI writeRequest(WARCWriter warc, String url, String timestamp, Recorder recorder, URI responseId) throws IOException {
        try (InputStream stream = recorder.getRecordedOutput().getReplayInputStream()) {
            URI recordId = uuid.getRecordID();
            WARCRecordInfo record = new WARCRecordInfo();
            record.setType(WARCConstants.WARCRecordType.request);
            record.setUrl(url);
            record.setCreate14DigitDate(timestamp);
            record.setMimetype(HTTP_REQUEST_MIMETYPE);
            record.setContentLength(recorder.getRecordedOutput().getSize());
            record.setEnforceLength(true);
            record.setRecordId(recordId);
            record.setContentStream(stream);
            ANVLRecord extraHeaders = new ANVLRecord();
            extraHeaders.addLabelValue(HEADER_KEY_CONCURRENT_TO, "<" + responseId + ">");
            record.setExtraHeaders(extraHeaders);
            warc.writeRecord(record);
            return recordId;
        }
    }

    static URI writeResponse(WARCWriter warc, String url, String timestamp, Recorder recorder) throws IOException {
        try (InputStream stream = recorder.getRecordedInput().getReplayInputStream()) {
            URI recordId = uuid.getRecordID();
            WARCRecordInfo record = new WARCRecordInfo();
            record.setType(WARCConstants.WARCRecordType.response);
            record.setUrl(url);
            record.setCreate14DigitDate(timestamp);
            record.setMimetype(HTTP_RESPONSE_MIMETYPE);
            record.setContentLength(recorder.getRecordedInput().getSize());
            record.setEnforceLength(true);
            record.setRecordId(recordId);
            record.setContentStream(stream);
            warc.writeRecord(record);
            return recordId;
        }
    }

    private static long parseContentLength(CloseableHttpResponse response) {
        Header h = response.getLastHeader("content-length");
        if (h != null) {
            String s = h.getValue().trim();
            if (!s.isEmpty()) {
                return Long.parseLong(s);
            }
        }
        return -1;
    }

    private String parseContentType(CloseableHttpResponse response) {
        Header header = response.getLastHeader("content-type");
        if (header != null) {
            return header.getValue().split(";", 2)[0].trim();
        }
        return "application/octet-stream";
    }
}
