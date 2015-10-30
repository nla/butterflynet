package butterflynet;

import droute.Tokens;
import org.junit.*;

import static org.junit.Assert.*;

public class DbTest {
    static DbPool dbPool;
    Db db;

    @BeforeClass
    public static void setupClass() {
        dbPool = new DbPool("jdbc:h2:mem:DbTest;MODE=MYSQL;INIT=CREATE SCHEMA IF NOT EXISTS \"public\"", "sa", "");
    }

    @AfterClass
    public static void teardownClass() {
        if (dbPool != null) {
            dbPool.close();
        }
    }

    @Before
    public void setup() {
        db = dbPool.take();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testUserAndSessionCreation() {
        long joeId = db.upsertUser("joe", "example.org", "joe", "Joe Testuser", "joe@example.org");
        long maryId = db.upsertUser("mary", "example.org", "mary", "Joe Testuser", "mary@example.org");

        assertNotEquals(joeId, maryId);

        String sid = Tokens.generate();
        db.insertSession(sid, joeId, 5000);

        UserInfo joe = db.findUserBySessionId(sid);
        assertEquals(joeId, joe.id);
        assertEquals("joe", joe.username);
        assertEquals("example.org", joe.issuer);
        assertEquals("joe", joe.subject);
        assertEquals("Joe Testuser", joe.name);
        assertEquals("joe@example.org", joe.email);

        db.expireSessions(6000);
        assertNull("sessions should expire", db.findUserBySessionId(sid));

        long jimId = db.upsertUser("jim", "example.org", "joe", "Jim Testuser", "joe@example.org");
        assertEquals(joeId, jimId);
    }

    @Test
    public void testAllowedMediaTypes() {
        db.insertAllowedMediaType("hello/world");
        assertTrue(db.listAllowedMediaTypes().contains("hello/world"));
        db.deleteAllowedMediaType("hello/world");
        assertFalse(db.listAllowedMediaTypes().contains("hello/world"));
    }
}
