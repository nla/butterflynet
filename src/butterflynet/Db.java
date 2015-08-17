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

        Capture(ResultSet rs) throws SQLException {
            id = rs.getLong("id");
            url = rs.getString("url");
            started = rs.getTimestamp("started");
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

    @SqlUpdate("UPDATE capture SET archived = :archived, status = :status, reason = :reason, size = :size WHERE id = :id")
    int setCaptureArchived(@Bind("id") long id, @Bind("archived") Date archived, @Bind("status") int status, @Bind("reason") String reason, @Bind("size") long size);

    void setCaptureFailed(long id, Date date, String message);

    void close();

}
