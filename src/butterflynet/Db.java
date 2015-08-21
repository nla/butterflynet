package butterflynet;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public interface Db extends AutoCloseable {

    static void registerMappers(DBI dbi) {
        dbi.registerMapper(new CaptureMapper());
    }

    class Capture {
        public final long id;
        public final String url;
        public final Date started;
        public final int state;
        public final String reason;
        public final int status;

        Capture(ResultSet rs) throws SQLException {
            id = rs.getLong("id");
            url = rs.getString("url");
            started = rs.getTimestamp("started");
            state = rs.getInt("state");
            status = rs.getInt("status");
            reason = rs.getString("reason");
        }

        public String getStateName() {
            switch (state) {
                case QUEUED: return "QUEUED";
                case ARCHIVED: return "ARCHIVED";
                case FAILED: return "FAILED";
                case DOWNLOADING: return "DOWNLOADING";
                default: return "UNKNOWN (" + state + ")";
            }
        }
    }

    class CaptureMapper implements ResultSetMapper<Capture> {
        @Override
        public Capture map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
            return new Capture(resultSet);
        }
    }

    @SqlUpdate("INSERT INTO capture SET url = :url, started = :started")
    @GetGeneratedKeys
    long insertCapture(@Bind("url") String url, @Bind("started") Date started);

    @SqlQuery("SELECT * FROM capture ORDER BY id DESC LIMIT 50")
    List<Capture> recentCaptures();

    @SqlQuery("SELECT * FROM capture WHERE state IN (" + QUEUED + ", " + DOWNLOADING + ") LIMIT :limit")
    List<Capture> findCapturesToArchive(@Bind("limit") int limit);

    @SqlUpdate("UPDATE capture SET state = " + DOWNLOADING + " WHERE id = :id")
    int setCaptureDownloading(@Bind("id") long id);

    @SqlUpdate("UPDATE capture SET archived = :archived, status = :status, reason = :reason, size = :size, state = " + ARCHIVED + " WHERE id = :id")
    int setCaptureArchived(@Bind("id") long id, @Bind("archived") Date archived, @Bind("status") int status, @Bind("reason") String reason, @Bind("size") long size);

    @SqlUpdate("UPDATE capture SET reason = :message, state = " + FAILED + " WHERE id = :id")
    void setCaptureFailed(@Bind("id") long id, @Bind("date") Date date, @Bind("message") String message);

    void close();

    int QUEUED = 0;
    int ARCHIVED = 1;
    int FAILED = 2;
    int DOWNLOADING = 3;
}
