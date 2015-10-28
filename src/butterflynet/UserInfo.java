package butterflynet;

import com.google.api.client.util.Key;

public class UserInfo {
    public final long id;

    /*
     * The @Key annotations below map the fields from the userinfo json response from the OpenID Connect server.
     */

    @Key("iss")
    public final String issuer;

    @Key("sub")
    public final String subject;

    @Key("preferred_username")
    public final String username;

    @Key
    public final String name;

    @Key
    public final String email;

    public UserInfo(long id, String issuer, String subject, String username, String name, String email) {
        this.id = id;
        this.issuer = issuer;
        this.subject = subject;
        this.username = username;
        this.name = name;
        this.email = email;
    }
}
