package butterflynet;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.logging.SLF4JLog;

public class DbPool implements AutoCloseable {
    final HikariDataSource ds;
    final DBI dbi;

    public DbPool(Config config) {
        this(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
    }

    public DbPool(String jdbcUrl, String user, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("ButterflynetDb");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        ds = new HikariDataSource(hikariConfig);
        migrate();
        dbi = new DBI(ds);
        Db.registerMappers(dbi);
        dbi.setSQLLog(new SLF4JLog() {
            @Override
            public void logObtainHandle(long time, Handle h) {
                // suppress unless unusually slow
                if (time > 10) {
                    super.logObtainHandle(time, h);
                }
            }

            @Override
            public void logReleaseHandle(Handle h) {
                // suppress
            }
        });
    }

    public void migrate() {
        Flyway flyway = new Flyway();
        flyway.setDataSource(ds);
        flyway.setLocations("butterflynet/migrations");
        flyway.migrate();
    }

    public Db take() {
        return dbi.open(Db.class);
    }

    @Override
    public void close() {
        ds.close();
    }
}
