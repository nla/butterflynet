package butterflynet;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public interface Db extends AutoCloseable {

    static void registerMappers(DBI dbi) {
        dbi.registerMapper(new CaptureMapper());
        dbi.registerMapper(new UserInfoMapper());
    }



    class Capture {
        public final long id;
        public final String url;
        public final Date started;
        public final Date archived;
        public final int state;
        public final String reason;
        public final int status;
        public final long size;

        Capture(ResultSet rs) throws SQLException {
            id = rs.getLong("id");
            url = rs.getString("url");
            started = rs.getTimestamp("started");
            archived = rs.getTimestamp("archived");
            state = rs.getInt("state");
            status = rs.getInt("status");
            reason = rs.getString("reason");
            size = rs.getLong("size");
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

    class UserInfoMapper implements ResultSetMapper<UserInfo> {
        @Override
        public UserInfo map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            UserInfo user = new UserInfo();
            user.username = r.getString("username");
            user.email = r.getString("email");
            user.issuer = r.getString("issuer");
            user.subject = r.getString("subject");
            return user;
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

    @SqlUpdate("INSERT INTO user (username, issuer, subject, name, email) " +
            "VALUES (:username, :issuer, :subject, :name, :email) " +
            "ON DUPLICATE KEY UPDATE " +
            "    issuer = VALUES(issuer)," +
            "    subject = VALUES(subject)," +
            "    name = VALUES(name)," +
            "    email = VALUES(email)")
    void upsertUser(@Bind("username") String username, @Bind("issuer") String issuer, @Bind("subject") String subject, @Bind("name") String name, @Bind("email") String email);

    @SqlQuery("SELECT user.* FROM user, session WHERE user.username = session.username AND session.id = :sessionId")
    UserInfo findUserBySessionId(@Bind("sessionId") String sessionId);

    @SqlUpdate("INSERT INTO session (id, username, expiry) VALUES (:sessionId, :username, :expiry)")
    void insertSession(@Bind("sessionId") String sessionId, @Bind("username") String username, @Bind("expiry") long expiry);

    @SqlUpdate("DELETE FROM session WHERE expiry > :now")
    void expireSessions(@Bind("now") long now);

    @SqlUpdate("UPDATE capture SET state = " + FAILED + ", reason = 'Cancelled' WHERE id = :id AND state = " + QUEUED)
    int cancelCapture(@Bind("id") long id);

    void close();

    int QUEUED = 0;
    int ARCHIVED = 1;
    int FAILED = 2;
    int DOWNLOADING = 3;
}
