package butterflynet;

import java.io.File;

public class Config {
    public String getWarcPrefix() {
        return System.getenv().getOrDefault("BUTTERFLYNET_WARC_PREFIX", "WEB");
    }

    public File getWarcDir() {
        return new File(System.getenv().getOrDefault("BUTTERFLYNET_WARC_DIR", "./warcs/"));
    }

    public String getDbUrl() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_URL", "jdbc:h2:mem:butterflynet");
    }

    public String getDbUser() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_USER", "butterflynet");
    }

    public String getDbPassword() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_PASSWORD", "butterflynet");
    }

    public String getReplayUrl() {
        return System.getenv().getOrDefault("BUTTERFLYNET_REPLAY_URL", "");
    }
}
