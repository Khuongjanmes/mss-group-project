package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.openevent.common.web.ApiException;
import com.openevent.identity.auth.google.GoogleIdentity;
import com.openevent.identity.support.IdentityIntegrationTest;

import org.springframework.http.HttpStatus;

class GoogleLoginIT extends IdentityIntegrationTest {

    private Response googleLogin(GoogleIdentity identity) {
        when(googleVerifier.verify(anyString())).thenReturn(identity);
        return post("/api/auth/google", Map.of("idToken", "token-from-google"));
    }

    private static GoogleIdentity identity(String subject, String email) {
        return new GoogleIdentity(subject, email, "Google User", "https://lh3.example.com/p.png", true);
    }

    @Test
    void firstLoginCreatesAttendeeWithoutPassword() {
        String email = uniqueEmail();
        String subject = UUID.randomUUID().toString();

        Response response = googleLogin(identity(subject, email));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("user").path("email").asString()).isEqualTo(email);
        assertThat(response.body().path("user").path("provider").asString()).isEqualTo("GOOGLE");
        assertThat(response.body().path("user").path("roles").toString()).isEqualTo("[\"ATTENDEE\"]");
        assertThat(jdbc.queryForObject("select password_hash from users where email = ?", String.class, email)).isNull();
    }

    @Test
    void secondLoginReusesAccountEvenIfGoogleEmailChanged() {
        String subject = UUID.randomUUID().toString();
        String firstId = googleLogin(identity(subject, uniqueEmail())).body().path("user").path("id").asString();

        Response second = googleLogin(identity(subject, uniqueEmail()));

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().path("user").path("id").asString()).isEqualTo(firstId);
    }

    @Test
    void doesNotTakeOverExistingPasswordAccountWithSameEmail() {
        String email = uniqueEmail();
        String originalId = register(email).body().path("user").path("id").asString();

        Response response = googleLogin(identity(UUID.randomUUID().toString(), email));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.code()).isEqualTo("EMAIL_REGISTERED_WITH_PASSWORD");
        assertThat(jdbc.queryForObject("select provider from users where id = ?::uuid", String.class, originalId)).isEqualTo("LOCAL");
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        Response response = googleLogin(new GoogleIdentity(UUID.randomUUID().toString(), uniqueEmail(), "G", null, false));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("INVALID_GOOGLE_TOKEN");
    }

    @Test
    void invalidTokenIsRejected() {
        when(googleVerifier.verify(anyString()))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Token Google không hợp lệ"));

        Response response = post("/api/auth/google", Map.of("idToken", "forged"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("INVALID_GOOGLE_TOKEN");
    }

    @Test
    void missingTokenIsBadRequest() {
        assertThat(post("/api/auth/google", Map.of()).status()).isEqualTo(400);
    }

    @Test
    void lockedGoogleAccountCannotLogin() {
        String subject = UUID.randomUUID().toString();
        String email = uniqueEmail();
        String id = googleLogin(identity(subject, email)).body().path("user").path("id").asString();
        send("PUT", "/api/users/" + id + "/lock", null, admin());

        Response response = googleLogin(identity(subject, email));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void googleAccountCannotLoginWithPasswordOrChangePassword() {
        String email = uniqueEmail();
        Response auth = googleLogin(identity(UUID.randomUUID().toString(), email));

        assertThat(login(email, PASSWORD).code()).isEqualTo("INVALID_CREDENTIALS");
        Response change = send("PUT", "/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "New-Passw0rd"), attendee(auth));
        assertThat(change.status()).isEqualTo(400);
        assertThat(change.code()).isEqualTo("NO_LOCAL_PASSWORD");
    }

    @Test
    void registeringWithEmailOfGoogleAccountIsRejected() {
        String email = uniqueEmail();
        googleLogin(identity(UUID.randomUUID().toString(), email));

        assertThat(register(email).code()).isEqualTo("EMAIL_TAKEN");
    }
}
