package butterflynet;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.*;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public abstract class Db implements AutoCloseable {

    static void registerMappers(DBI dbi) {
        dbi.registerMapper(new CaptureMapper());
        dbi.registerMapper(new CaptureDetailedMapper());
        dbi.registerMapper(new UserInfoMapper());
    }

   public static class Capture {
        public final long id;
        public final String url;
        public final Date started;
        public final Date archived;
        public final int state;
        public final String reason;
        public final int status;
        public final long size;
        public final long userId;

        Capture(ResultSet rs) throws SQLException {
            id = rs.getLong("id");
            url = rs.getString("url");
            started = rs.getTimestamp("started");
            archived = rs.getTimestamp("archived");
            state = rs.getInt("state");
            status = rs.getInt("status");
            reason = rs.getString("reason");
            size = rs.getLong("size");
            userId = rs.getLong("user_id");
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

    public static class CaptureDetailed extends Capture {
        public final String username;

        CaptureDetailed(ResultSet rs) throws SQLException {
            super(rs);
            username = rs.getString("username");
        }
    }

    static class CaptureMapper implements ResultSetMapper<Capture> {
        @Override
        public Capture map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
            return new Capture(resultSet);
        }
    }

    static class CaptureDetailedMapper implements ResultSetMapper<CaptureDetailed> {
        @Override
        public CaptureDetailed map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
            return new CaptureDetailed(resultSet);
        }
    }
    static class UserInfoMapper implements ResultSetMapper<UserInfo> {
        @Override
        public UserInfo map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new UserInfo(r.getLong("id"), r.getString("issuer"), r.getString("subject"),
                    r.getString("username"), r.getString("name"), r.getString("email"));
        }
    }

    @SqlUpdate("INSERT INTO capture SET url = :url, started = :started, user_id = :userId")
    @GetGeneratedKeys
    public abstract long insertCapture(@Bind("url") String url, @Bind("started") Date started, @Bind("userId") long userId);

    @SqlQuery("SELECT capture.*, user.username FROM capture LEFT JOIN user ON user.id = capture.user_id ORDER BY capture.id DESC LIMIT 50")
    public abstract List<CaptureDetailed> recentCaptures();

    @SqlQuery("SELECT * FROM capture WHERE state IN (" + QUEUED + ", " + DOWNLOADING + ") LIMIT :limit")
    public abstract List<Capture> findCapturesToArchive(@Bind("limit") int limit);

    @SqlUpdate("UPDATE capture SET state = " + DOWNLOADING + " WHERE id = :id")
    public abstract int setCaptureDownloading(@Bind("id") long id);

    @SqlUpdate("UPDATE capture SET archived = :archived, status = :status, reason = :reason, size = :size, state = " + ARCHIVED + " WHERE id = :id")
    public abstract int setCaptureArchived(@Bind("id") long id, @Bind("archived") Date archived, @Bind("status") int status, @Bind("reason") String reason, @Bind("size") long size);

    @SqlUpdate("UPDATE capture SET reason = :message, state = " + FAILED + " WHERE id = :id")
    public abstract void setCaptureFailed(@Bind("id") long id, @Bind("date") Date date, @Bind("message") String message);

    @SqlUpdate("INSERT INTO user (username, issuer, subject, name, email) " +
            "VALUES (:username, :issuer, :subject, :name, :email) " +
            "ON DUPLICATE KEY UPDATE " +
            "    issuer = VALUES(issuer), " +
            "    subject = VALUES(subject), " +
            "    name = VALUES(name), " +
            "    email = VALUES(email)")
    public abstract int upsertUserInternal(@Bind("username") String username, @Bind("issuer") String issuer, @Bind("subject") String subject, @Bind("name") String name, @Bind("email") String email);

    @SqlQuery("SELECT user.id FROM user WHERE issuer = :issuer AND subject = :subject")
    public abstract long findUserId(@Bind("issuer") String issuer, @Bind("subject") String subject);

    long upsertUser(String username, String issuer, String subject, String name, String email) {
        upsertUserInternal(username, issuer, subject, name, email);
        return findUserId(issuer, subject);
    }

    @SqlQuery("SELECT user.* FROM user, session WHERE user.id = session.user_id AND session.id = :sessionId")
    public abstract UserInfo findUserBySessionId(@Bind("sessionId") String sessionId);

    @SqlUpdate("INSERT INTO session (id, user_id, expiry) VALUES (:sessionId, :userId, :expiry)")
    public abstract void insertSession(@Bind("sessionId") String sessionId, @Bind("userId") long userId, @Bind("expiry") long expiry);

    @SqlUpdate("DELETE FROM session WHERE expiry < :now")
    public abstract void expireSessions(@Bind("now") long now);

    @SqlUpdate("UPDATE capture SET state = " + FAILED + ", reason = 'Cancelled' WHERE id = :id AND state = " + QUEUED)
    public abstract int cancelCapture(@Bind("id") long id);

    @SqlQuery("SELECT media_type FROM allowed_media_type ORDER BY media_type ASC")
    public abstract List<String> listAllowedMediaTypes();

    @SqlUpdate("INSERT INTO allowed_media_type (media_type) VALUES (:mediaType)")
    public abstract void insertAllowedMediaType(@Bind("mediaType") String mediaType);

    @SqlUpdate("DELETE FROM allowed_media_type WHERE media_type = :mediaType")
    public abstract void deleteAllowedMediaType(@Bind("mediaType") String mediaType);

    public abstract void close();

    public static final int QUEUED = 0;
    public static final int ARCHIVED = 1;
    public static final int FAILED = 2;
    public static final int DOWNLOADING = 3;
}
