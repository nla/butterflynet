package butterflynet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

public class Butterflynet {
    final Logger log = LoggerFactory.getLogger(Butterflynet.class);
    final Config config = new Config();
    final DbPool dbPool = new DbPool(config);
    final HttpArchiver archiver = new HttpArchiver(config.getWarcPrefix(), config.getWarcDir());

    void run() {
        try (Db db = dbPool.take()) {
            Db.Capture capture = db.recentCaptures().get(0);
            try {
                log.debug("Begin archiving capture id={} url={}", capture.id, capture.url);
                HttpArchiver.Result result = archiver.archive(capture.url);
                log.debug("Successfully archived capture id={} url={}", capture.id, capture.url);
                db.setCaptureArchived(capture.id, result.timestamp, result.status, result.reason, result.size);
            } catch (IOException | InterruptedException e) {
                db.setCaptureFailed(capture.id, new Date(), e.getMessage());
                log.error("Error archiving capture id={} url={}", capture.id, capture.url, e);
            }
        }
    }

    /**
     * Queues a URL for archiving.
     *
     * @return capture id of the submitted url
     */
    public long submit(String url) {
        try (Db db = dbPool.take()) {
            return db.insertCapture(url, new Date());
        }
    }
}
