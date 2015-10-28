package butterflynet;

import java.io.File;
import java.util.Map;

public class Config {
    final Map<String, String> env;

    public Config() {
        this(System.getenv());
    }

    public Config(Map<String, String> env) {
        this.env = env;
    }

    public String getWarcPrefix() {
        return env.getOrDefault("BUTTERFLYNET_WARC_PREFIX", "WEB");
    }

    public File getWarcDir() {
        return new File(env.getOrDefault("BUTTERFLYNET_WARC_DIR", "./warcs/"));
    }

    public String getDbUrl() {
        return env.getOrDefault("BUTTERFLYNET_DB_URL", "jdbc:h2:mem:butterflynet;MODE=MYSQL;INIT=CREATE SCHEMA IF NOT EXISTS \"public\"");
    }

    public String getDbUser() {
        return env.getOrDefault("BUTTERFLYNET_DB_USER", "butterflynet");
    }

    public String getDbPassword() {
        return env.getOrDefault("BUTTERFLYNET_DB_PASSWORD", "butterflynet");
    }

    public String getOAuthServer() {
        return env.get("OAUTH_SERVER");
    }

    public String getOAuthClientId() {
        return env.get("OAUTH_CLIENT_ID");
    }

    public String getOAuthClientSecret() {
        return env.get("OAUTH_CLIENT_SECRET");
    }

    public String getReplayUrl() {
        return env.getOrDefault("BUTTERFLYNET_REPLAY_URL", "");
    }

    public boolean isWorkerEnabled() {
        return !Boolean.valueOf(env.getOrDefault("DISABLE_WORKER", "false"));
    }
}
