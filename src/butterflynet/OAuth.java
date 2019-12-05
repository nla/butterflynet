package butterflynet;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.nio.charset.StandardCharsets.UTF_8;

final class OAuth {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    final NetHttpTransport http = new NetHttpTransport();
    final JsonFactory json = new JacksonFactory();
    final String serverUrl;
    final String clientId;
    final String clientSecret;
    private OpenIDConfig config;
    private AuthorizationCodeFlow authFlow;

    OAuth(String serverUrl, String clientId, String clientSecret) {
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public AuthorizationCodeFlow authFlow() {
        if (authFlow == null) {
            authFlow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                    http, json,
                    new GenericUrl(config().token_endpoint),
                    new BasicAuthentication(clientId, clientSecret),
                    clientId,
                    config().authorization_endpoint).build();
        }
        return authFlow;
    }

    private OpenIDConfig config() {
        if (config == null) {
            String configUrl = serverUrl.replaceFirst("/+$", "") + "/.well-known/openid-configuration";
            try (InputStream stream = new URL(configUrl).openStream()) {
                this.config = objectMapper.readValue(stream, OpenIDConfig.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return config;
    }

    /**
     * Generates the OIDC server URL to redirect to for the login page.
     */
    String authUrl(String csrfToken, String redirectUri) {
        return authFlow().newAuthorizationUrl()
                .setState(sha256(csrfToken))
                .setScopes(Collections.singletonList("openid"))
                .setRedirectUri(redirectUri)
                .build();
    }

    /**
     * Called when the OIDC server redirects back to us after the user is logged in.
     * We ask the OIDC server for their details (username, email address etc) and
     * return it to store in our user session.
     */
    UserInfo authCallback(String csrfToken, String authCode, String state, String redirectUri) {
        if (!state.equals(sha256(csrfToken))) {
            throw new IllegalArgumentException("Incorrect OAuth state, possible CSRF attack");
        }
        try {
            TokenResponse tokenResponse = authFlow().newTokenRequest(authCode)
                    .setGrantType("authorization_code")
                    .setRedirectUri(redirectUri)
                    .execute();
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(tokenResponse.getAccessToken());
            GenericUrl url = new GenericUrl(config().userinfo_endpoint);
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
            return hexEncode(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String hexEncode(byte[] bytes) {
        String alphabet = "0123456789abcdef";
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = alphabet.charAt((bytes[i] & 0xFF) >>> 4);
            chars[i * 2 + 1] = alphabet.charAt(bytes[i] & 0xf);
        }
        return new String(chars);
    }

    public static class OpenIDConfig {
        public String authorization_endpoint;
        public String token_endpoint;
        public String userinfo_endpoint;
    }
}
