package butterflynet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Butterflynet implements AutoCloseable {
    final Logger log = LoggerFactory.getLogger(Butterflynet.class);
    final Config config;
    final DbPool dbPool;
    final HttpArchiver archiver;
    final int batchSize = 50;
    final Map<Long, HttpArchiver.Progress> progressMap = new ConcurrentHashMap<>();
    Thread worker;
    Long currentCapture;

    public Butterflynet(Config config) {
        this.config = config;
        dbPool = new DbPool(config);
        archiver = new HttpArchiver(config.getWarcPrefix(), config.getWarcDir());
    }

    synchronized void startWorker() {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::processQueue);
            worker.setName("worker");
            worker.start();
        }
    }

    synchronized void setCurrentCapture(Long captureId) {
       currentCapture = captureId;
    }

    private void processQueue() {
        List<Db.Capture> captures;
        do {
            List<String> allowedMediaTypes;
            try (Db db = dbPool.take()) {
                captures = db.findCapturesToArchive(batchSize);
                allowedMediaTypes = db.listAllowedMediaTypes();
            }
            for (Db.Capture capture : captures) {
                try {
                    log.debug("Begin archiving capture id={} url={}", capture.id, capture.url);
                    setCurrentCapture(capture.id);
                    try (Db db = dbPool.take()) {
                        db.setCaptureDownloading(capture.id);
                    }

                    HttpArchiver.Result result = archiver.archive(capture.url, (p) -> {
                        progressMap.put(capture.id, p);
                    }, allowedMediaTypes);
                    log.debug("Successfully archived capture id={} url={}", capture.id, capture.url);
                    try (Db db = dbPool.take()) {
                        db.setCaptureArchived(capture.id, result.timestamp, result.status, result.reason, result.size);
                    }
                } catch (InterruptedException e) {
                    try (Db db = dbPool.take()) {
                        db.setCaptureFailed(capture.id, new Date(), "Cancelled");
                    }
                    log.error("Archiving cancelled id={} url={}", capture.id, capture.url, e);

                    // ensure interrupted flag is cleared
                    Thread.currentThread().interrupted();
                } catch (Exception e) {
                    try (Db db = dbPool.take()) {
                        db.setCaptureFailed(capture.id, new Date(), e.getMessage());
                    }
                    log.error("Error archiving capture id={} url={}", capture.id, capture.url, e);
                } finally {
                    setCurrentCapture(null);
                    progressMap.remove(capture.id);
               }
            }
        } while (!captures.isEmpty());
    }

    HttpArchiver.Progress getProgress(long captureId) {
        return progressMap.get(captureId);
    }

    /**
     * Queues a URL for archiving.
     *
     * @return capture id of the submitted url
     */
    public long submit(String url, long userId) {
        long id;
        try (Db db = dbPool.take()) {
            id = db.insertCapture(url, new Date(), userId);
        }
        startWorker();
        return id;
    }

    public synchronized void cancel(long id) {
        if (Objects.equals(currentCapture, id)) {
            worker.interrupt();
        }
        try (Db db = dbPool.take()) {
            db.cancelCapture(id);
        }
    }

    public void close() {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
            }
        }
        archiver.close();
        dbPool.close();
    }
}
