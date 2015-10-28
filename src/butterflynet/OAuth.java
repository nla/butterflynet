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
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

final class OAuth {
    final NetHttpTransport http = new NetHttpTransport();
    final JsonFactory json = new GsonFactory();
    final AuthorizationCodeFlow authFlow;

    final String serverUrl;
    final String clientId;
    final String clientSecret;

    OAuth(String serverUrl, String clientId, String clientSecret) {
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        authFlow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                http, json,
                new GenericUrl(serverUrl + "/token"),
                new BasicAuthentication(clientId, clientSecret),
                clientId,
                serverUrl + "/authorize").build();
    }

    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data.getBytes(UTF_8));
            return Base64.getUrlEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    String authUrl(String csrfToken) {
        return authFlow.newAuthorizationUrl()
                .setState(sha256(csrfToken))
                .build();
    }

    UserInfo authCallback(String csrfToken, String authCode, String state) {
        if (!state.equals(sha256(csrfToken))) {
            throw new IllegalArgumentException("Incorrect OAuth state, possible CSRF attack");
        }
        try {
            TokenResponse tokenResponse = authFlow.newTokenRequest(authCode).execute();
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(tokenResponse.getAccessToken());
            GenericUrl url = new GenericUrl(serverUrl + "/userinfo");
            HttpResponse response = http.createRequestFactory(credential)
                    .buildGetRequest(url)
                    .setParser(json.createJsonObjectParser())
                    .execute();
            return response.parseAs(UserInfo.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}