package butterflynet;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.archive.format.warc.WARCConstants;
import org.archive.io.warc.*;
import org.archive.uid.UUIDGenerator;
import org.archive.util.ArchiveUtils;
import org.archive.util.Recorder;
import org.archive.util.anvl.ANVLRecord;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.archive.format.warc.WARCConstants.*;

public class HttpArchiver {
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

    static String getPid() {
        // FIXME: replace with ProcessHandle.current().getPid() on Java 9
        return ManagementFactory.getRuntimeMXBean().getName().split("@", 2)[0];
    }

    static String getHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    static class Result {
        Date timestamp;
        int status;
        String reason;
        long size;
    }

    Result archive(String url) throws IOException, InterruptedException {
        Result result = new Result();
        HttpGet request = new HttpGet(url);
        Recorder recorder = new Recorder(new File(System.getProperty("java.io.tmpdir")), "butterflynet-http-");
        Date date = new Date();
        String timestamp = ArchiveUtils.getLog14Date(date);
        try (CloseableHttpClient client = RecordingHttpClient.create(recorder);
             CloseableHttpResponse response = client.execute(request)) {
            recorder.getRecordedInput().readToEndOfContent(parseContentLength(response));
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
        } finally {
            warcPool.returnFile(warc);
        }
        return result;
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
}
