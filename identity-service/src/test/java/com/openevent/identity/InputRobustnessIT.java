package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.openevent.identity.support.IdentityIntegrationTest;

/** Dữ liệu đầu vào bất thường phải trả 4xx rõ ràng, không được thành 500. */
class InputRobustnessIT extends IdentityIntegrationTest {

    @Test
    void nullCharacterInFullNameIsRejected() {
        Response response = post("/api/auth/register",
                Map.of("email", uniqueEmail(), "password", PASSWORD, "fullName", "bad\u0000name"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void nullCharacterInProfileIsRejected() {
        Response auth = register(uniqueEmail());

        Response response = send("PUT", "/api/users/me", Map.of("fullName", "bad\u0000name"), attendee(auth));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void nullCharacterInLoginEmailIsRejected() {
        assertThat(post("/api/auth/login", Map.of("email", "a\u0000@example.com", "password", PASSWORD)).status())
                .isEqualTo(400);
    }

    @Test
    void nullCharacterInSearchKeywordIsRejected() {
        assertThat(send("GET", "/api/users?keyword=%00", null, admin()).status()).isEqualTo(400);
    }

    @Test
    void veryLongPasswordOnLoginIsJustWrongCredentials() {
        String email = uniqueEmail();
        register(email);

        Response response = login(email, "x".repeat(5000));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void veryLongRefreshTokenIsJustInvalid() {
        assertThat(refresh("x".repeat(5000)).status()).isEqualTo(401);
    }

    @Test
    void passwordOf72AsciiBytesIsAcceptedAndWorks() {
        String email = uniqueEmail();
        String password = "a".repeat(72);

        Response response = post("/api/auth/register", Map.of("email", email, "password", password, "fullName", "A"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(login(email, password).status()).isEqualTo(200);
    }

    @Test
    void passwordOf73BytesIsRejected() {
        Response response = post("/api/auth/register",
                Map.of("email", uniqueEmail(), "password", "a".repeat(73), "fullName", "A"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void vietnamesePasswordWorks() {
        String email = uniqueEmail();
        String password = "mậtkhẩuViệtNam";

        assertThat(post("/api/auth/register", Map.of("email", email, "password", password, "fullName", "A")).status())
                .isEqualTo(201);
        assertThat(login(email, password).status()).isEqualTo(200);
    }

    @Test
    void lockedAdminWithStillValidTokenCannotUseAdminApis() {
        String id = register(uniqueEmail()).body().path("user").path("id").asString();
        Integer adminRole = 3;
        jdbc.update("insert into user_roles (user_id, role_id) values (?::uuid, ?)", id, adminRole);
        Caller lockedAdmin = new Caller(id, "ADMIN");
        send("PUT", "/api/users/" + id + "/lock", null, admin());

        Response response = send("GET", "/api/users", null, lockedAdmin);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(send("PUT", "/api/users/" + UUID.randomUUID() + "/unlock", null, lockedAdmin).code())
                .isEqualTo("ACCOUNT_LOCKED");
    }
}
