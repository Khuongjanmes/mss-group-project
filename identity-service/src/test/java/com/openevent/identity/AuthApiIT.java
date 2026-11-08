package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.openevent.identity.support.IdentityIntegrationTest;

class AuthApiIT extends IdentityIntegrationTest {

    private static <T> List<T> runConcurrently(int count, Callable<T> task) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(task));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    @Test
    void registerCreatesAttendeeAndReturnsTokens() {
        String email = uniqueEmail();

        Response response = register(email);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(response.body().path("accessToken").asString()).isNotBlank();
        assertThat(response.body().path("refreshToken").asString()).isNotBlank();
        assertThat(response.body().path("expiresIn").asLong()).isEqualTo(900);
        assertThat(response.body().path("user").path("email").asString()).isEqualTo(email);
        assertThat(response.body().path("user").path("fullName").asString()).isEqualTo("Nguyễn Văn A");
        assertThat(response.body().path("user").path("roles").toString()).isEqualTo("[\"ATTENDEE\"]");
    }

    @Test
    void responseNeverContainsPasswordHash() {
        Response response = register(uniqueEmail());

        assertThat(response.raw()).doesNotContainIgnoringCase("password");
    }

    @Test
    void passwordIsStoredAsBcryptAndRefreshTokenAsHash() {
        String email = uniqueEmail();
        Response response = register(email);

        String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);

        String raw = response.body().path("refreshToken").asString();
        Integer plainRows = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw);
        assertThat(plainRows).isZero();
    }

    @Test
    void emailIsNormalizedToLowercaseAndDuplicateIsRejectedCaseInsensitively() {
        String email = uniqueEmail();
        assertThat(register(email.toUpperCase().replace("EXAMPLE.COM", "Example.com")).body()
                .path("user").path("email").asString()).isEqualTo(email);

        Response duplicate = register(email);

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.code()).isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void concurrentRegistrationsWithSameEmailCreateExactlyOneAccount() throws Exception {
        String email = uniqueEmail();

        List<Integer> statuses = runConcurrently(8, () -> register(email).status());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
    }

    @Test
    void registerValidatesInput() {
        Response response = post("/api/auth/register", Map.of("email", "not-an-email", "password", "short", "fullName", " "));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.body().path("fieldErrors").propertyNames()).contains("email", "password", "fullName");
    }

    @Test
    void passwordOver72BytesIsRejectedEvenWhenUnder72Characters() {
        String multibyte = "ệ".repeat(30); // 30 ký tự nhưng 90 byte

        Response response = post("/api/auth/register", Map.of("email", uniqueEmail(), "password", multibyte, "fullName", "A"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("fieldErrors").has("password")).isTrue();
    }

    @Test
    void nonAsciiEmailIsRejectedBecauseItIsForwardedAsHttpHeader() {
        Response response = post("/api/auth/register", Map.of("email", "việt@example.com", "password", PASSWORD, "fullName", "A"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void malformedJsonIsBadRequestNotServerError() {
        Response response = post("/api/auth/login", "{not json");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("status").asInt()).isEqualTo(400);
    }

    @Test
    void loginReturnsTokensForValidCredentials() {
        String email = uniqueEmail();
        register(email);

        Response response = login(email, PASSWORD);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("accessToken").asString()).isNotBlank();
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() {
        String email = uniqueEmail();
        register(email);

        assertThat(login(email.toUpperCase(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        String email = uniqueEmail();
        register(email);

        Response wrongPassword = login(email, "Wrong-pass1");
        Response unknown = login(uniqueEmail(), "Wrong-pass1");

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrongPassword.code()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(unknown.code());
        assertThat(wrongPassword.body().path("message")).isEqualTo(unknown.body().path("message"));
    }

    @Test
    void accessTokenHasExpectedClaimsAndSignature() {
        String email = uniqueEmail();
        Response response = register(email);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(JWT_SECRET.getBytes(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        Jwt jwt = decoder.decode(response.body().path("accessToken").asString());

        assertThat(jwt.getSubject()).isEqualTo(response.body().path("user").path("id").asString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ATTENDEE");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("openevent-identity");
        assertThat(jwt.getExpiresAt()).isBetween(Instant.now().plusSeconds(890), Instant.now().plusSeconds(901));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void refreshRotatesTokens() {
        Response auth = register(uniqueEmail());
        String oldToken = auth.body().path("refreshToken").asString();

        Response refreshed = refresh(oldToken);

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.body().path("refreshToken").asString()).isNotEqualTo(oldToken);
        assertThat(refreshed.body().path("accessToken").asString()).isNotBlank();
    }

    @Test
    void reusingRotatedTokenRevokesAllTokensOfThatUser() {
        Response auth = register(uniqueEmail());
        String stolen = auth.body().path("refreshToken").asString();
        String current = refresh(stolen).body().path("refreshToken").asString();

        Response reuse = refresh(stolen);

        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.code()).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(refresh(current).status()).isEqualTo(401);
    }

    @Test
    void concurrentRefreshOfSameTokenSucceedsAtMostOnce() throws Exception {
        String token = register(uniqueEmail()).body().path("refreshToken").asString();

        List<Integer> statuses = runConcurrently(8, () -> refresh(token).status());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 401).hasSize(7);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        Response auth = register(uniqueEmail());
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where user_id = ?::uuid",
                auth.body().path("user").path("id").asString());

        assertThat(refresh(auth.body().path("refreshToken").asString()).status()).isEqualTo(401);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThat(refresh("does-not-exist").status()).isEqualTo(401);
    }

    @Test
    void logoutRevokesTokenAndIsIdempotent() {
        String token = register(uniqueEmail()).body().path("refreshToken").asString();

        assertThat(post("/api/auth/logout", Map.of("refreshToken", token)).status()).isEqualTo(204);
        assertThat(post("/api/auth/logout", Map.of("refreshToken", token)).status()).isEqualTo(204);
        assertThat(post("/api/auth/logout", Map.of("refreshToken", "unknown")).status()).isEqualTo(204);
        assertThat(refresh(token).status()).isEqualTo(401);
    }

    @Test
    void lockedAccountCannotLoginOrRefresh() {
        String email = uniqueEmail();
        Response auth = register(email);
        String id = auth.body().path("user").path("id").asString();
        assertThat(send("PUT", "/api/users/" + id + "/lock", null, admin()).status()).isEqualTo(200);

        Response login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.code()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(refresh(auth.body().path("refreshToken").asString()).status()).isEqualTo(401);
    }

    @Test
    void lockedAccountWithWrongPasswordDoesNotRevealLock() {
        String email = uniqueEmail();
        String id = register(email).body().path("user").path("id").asString();
        send("PUT", "/api/users/" + id + "/lock", null, admin());

        assertThat(login(email, "Wrong-pass1").code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unlockedAccountCanLoginAgain() {
        String email = uniqueEmail();
        String id = register(email).body().path("user").path("id").asString();
        Caller admin = admin();
        send("PUT", "/api/users/" + id + "/lock", null, admin);
        send("PUT", "/api/users/" + id + "/unlock", null, admin);

        assertThat(login(email, PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void bootstrapAdminCanLoginWithAdminRole() {
        Response response = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("user").path("roles").toString()).contains("ADMIN");
    }

    @Test
    void openApiDocumentIsAvailable() {
        Response response = send("GET", "/v3/api-docs", null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("paths").has("/api/auth/login")).isTrue();
    }
}
