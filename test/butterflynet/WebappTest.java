package butterflynet;

import droute.RequestBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class WebappTest {
    static Webapp webapp;
    static Db db;

    @Test
    public void test() {
        UserInfo user = new UserInfo(0, "example.org", "testuser", "testuser", "test user", "test@example.org");
        String sid = webapp.createSession(user, TimeUnit.MINUTES.toMillis(30));
        long userId = db.findUserId(user.issuer, user.subject);

        String target = "http://example.org/target";
        webapp.submit(RequestBuilder.post("/").formParam("url", target).cookie("id", sid).build());

        Db.Capture capture = db.recentCaptures().get(0);
        assertEquals(target, capture.url);
        assertEquals(userId, capture.userId);
    }

    @BeforeClass
    public static void setup() {
        Map<String,String> env = new HashMap<>();
        env.put("BUTTERFLYNET_DB_URL", "jdbc:h2:mem:WebappTest;MODE=MYSQL;INIT=CREATE SCHEMA IF NOT EXISTS \"public\"");
        webapp = new Webapp(new Config(env));
        db = webapp.butterflynet.dbPool.take();
    }

    @AfterClass
    public static void teardown() {
        if (webapp != null) {
            db.close();
            webapp.close();
        }
    }

}
