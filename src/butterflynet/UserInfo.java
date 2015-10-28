package butterflynet;

import com.google.api.client.util.Key;

public class UserInfo {
    public long id;

    /*
     * The @Key annotations below map the fields from the userinfo json response from the OpenID Connect server.
     */

    @Key("iss")
    public String issuer;

    @Key("sub")
    public String subject;

    @Key("preferred_username")
    public String username;

    @Key
    public String name;

    @Key
    public String email;
}
