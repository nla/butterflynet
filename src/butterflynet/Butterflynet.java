package butterflynet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class Butterflynet {
    final Logger log = LoggerFactory.getLogger(Butterflynet.class);
    final Config config = new Config();
    final DbPool dbPool = new DbPool(config);
    final HttpArchiver archiver = new HttpArchiver(config.getWarcPrefix(), config.getWarcDir());
    final int batchSize = 50;
    Thread worker;

    synchronized void startWorker() {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::processQueue);
            worker.setName("worker");
            worker.start();
        }
    }

    private void processQueue() {
        List<Db.Capture> captures;
        do {
            try (Db db = dbPool.take()) {
                captures = db.findCapturesInState(Db.QUEUED, batchSize);
            }
            for (Db.Capture capture : captures) {
                try {
                    log.debug("Begin archiving capture id={} url={}", capture.id, capture.url);
                    HttpArchiver.Result result = archiver.archive(capture.url);
                    log.debug("Successfully archived capture id={} url={}", capture.id, capture.url);
                    try (Db db = dbPool.take()) {
                        db.setCaptureArchived(capture.id, result.timestamp, result.status, result.reason, result.size);
                    }
                } catch (Exception e) {
                    try (Db db = dbPool.take()) {
                        db.setCaptureFailed(capture.id, new Date(), e.getMessage());
                    }
                    log.error("Error archiving capture id={} url={}", capture.id, capture.url, e);
                }
            }
        } while (!captures.isEmpty());
    }

    /**
     * Queues a URL for archiving.
     *
     * @return capture id of the submitted url
     */
    public long submit(String url) {
        long id;
        try (Db db = dbPool.take()) {
            id = db.insertCapture(url, new Date());
        }
        startWorker();
        return id;
    }

    public static void main(String args[]) throws IOException, InterruptedException {
        new Butterflynet().archiver.archive("http://localhost:8080/");
    }
}
