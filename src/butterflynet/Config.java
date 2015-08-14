package butterflynet;

public class Config {
    public String getDbUrl() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_URL", "jdbc:h2:mem:butterflynet");
    }

    public String getDbUser() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_USER", "butterflynet");
    }

    public String getDbPassword() {
        return System.getenv().getOrDefault("BUTTERFLYNET_DB_PASSWORD", "butterflynet");
    }
}
