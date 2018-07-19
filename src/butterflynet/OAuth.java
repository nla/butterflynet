package butterflynet;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

final class OAuth {
    final NetHttpTransport http = new NetHttpTransport();
    final JsonFactory json = new JacksonFactory();
    final AuthorizationCodeFlow authFlow;

    final String serverUrl;
    final String clientId;
    final String clientSecret;
    final String userinfoUrl;

    OAuth(String serverUrl, String clientId, String clientSecret) {
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        if (serverUrl.startsWith("https://login.microsoftonline.com/")) {
            userinfoUrl = "https://login.windows.net/common/openid/userinfo";
        } else {
            userinfoUrl = serverUrl + "/userinfo";
        }

        authFlow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                http, json,
                new GenericUrl(serverUrl + "/token"),
                new BasicAuthentication(clientId, clientSecret),
                clientId,
                serverUrl + "/authorize").build();
    }

    /**
     * Generates the OIDC server URL to redirect to for the login page.
     */
    String authUrl(String csrfToken) {
        return authFlow.newAuthorizationUrl()
                .setState(sha256(csrfToken))
                .setScopes(Arrays.asList("openid"))
                .build();
    }

    /**
     * Called when the OIDC server redirects back to us after the user is logged in.
     * We ask the OIDC server for their details (username, email address etc) and
     * return it to store in our user session.
     */
    UserInfo authCallback(String csrfToken, String authCode, String state) {
        if (!state.equals(sha256(csrfToken))) {
            throw new IllegalArgumentException("Incorrect OAuth state, possible CSRF attack");
        }
        try {
            TokenResponse tokenResponse = authFlow.newTokenRequest(authCode)
                    .setGrantType("authorization_code")
                    .execute();
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(tokenResponse.getAccessToken());
            GenericUrl url = new GenericUrl(userinfoUrl);
            HttpResponse response = http.createRequestFactory(credential)
                    .buildGetRequest(url)
                    .setParser(json.createJsonObjectParser())
                    .execute();
            UserInfo user = response.parseAs(UserInfo.class);
            user.issuer = serverUrl;
            return user;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    /**
     * Calculate a SHA-256 digest of a string.
     */
    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data.getBytes(UTF_8));
            return DatatypeConverter.printHexBinary(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
