package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.openevent.identity.support.IdentityIntegrationTest;

class UserApiIT extends IdentityIntegrationTest {

    private static final Map<String, Object> VALID_PROFILE = Map.of(
            "fullName", "Trần Thị B", "phone", "+84901234567", "avatarUrl", "https://cdn.example.com/a.png");

    @Test
    void anonymousCallerIsUnauthorizedWithJsonError() {
        Response response = send("GET", "/api/users/me", null, null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void malformedUserIdHeaderIsTreatedAsAnonymous() {
        assertThat(send("GET", "/api/users/me", null, new Caller("not-a-uuid", "ADMIN")).status()).isEqualTo(401);
    }

    @Test
    void meReturnsCurrentProfile() {
        String email = uniqueEmail();
        Response auth = register(email);

        Response me = send("GET", "/api/users/me", null, attendee(auth));

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("email").asString()).isEqualTo(email);
        assertThat(me.body().path("status").asString()).isEqualTo("ACTIVE");
        assertThat(me.body().path("provider").asString()).isEqualTo("LOCAL");
        assertThat(me.raw()).doesNotContainIgnoringCase("password");
    }

    @Test
    void meForUnknownUserIsUnauthorized() {
        Caller ghost = new Caller(UUID.randomUUID().toString(), "ATTENDEE");

        assertThat(send("GET", "/api/users/me", null, ghost).status()).isEqualTo(401);
    }

    @Test
    void lockedUserWithStillValidAccessTokenIsRejected() {
        Response auth = register(uniqueEmail());
        send("PUT", "/api/users/" + attendee(auth).id() + "/lock", null, admin());

        Response me = send("GET", "/api/users/me", null, attendee(auth));

        assertThat(me.status()).isEqualTo(403);
        assertThat(me.code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void updateProfileChangesOnlyProfileFields() {
        String email = uniqueEmail();
        Response auth = register(email);

        Response updated = send("PUT", "/api/users/me", VALID_PROFILE, attendee(auth));

        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().path("fullName").asString()).isEqualTo("Trần Thị B");
        assertThat(updated.body().path("phone").asString()).isEqualTo("+84901234567");
        assertThat(updated.body().path("avatarUrl").asString()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(updated.body().path("email").asString()).isEqualTo(email);
    }

    @Test
    void updateProfileIgnoresAttemptsToChangeEmailRolesOrStatus() {
        String email = uniqueEmail();
        Response auth = register(email);

        Response updated = send("PUT", "/api/users/me",
                Map.of("fullName", "X", "email", "evil@example.com", "roles", List.of("ADMIN"), "status", "LOCKED"),
                attendee(auth));

        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().path("email").asString()).isEqualTo(email);
        assertThat(updated.body().path("roles").toString()).doesNotContain("ADMIN");
        assertThat(updated.body().path("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void avatarUrlMustBeHttpOrHttps() {
        Response auth = register(uniqueEmail());

        Response response = send("PUT", "/api/users/me",
                Map.of("fullName", "X", "avatarUrl", "javascript:alert(1)"), attendee(auth));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("fieldErrors").has("avatarUrl")).isTrue();
    }

    @Test
    void phoneMustBeValid() {
        Response auth = register(uniqueEmail());

        Response response = send("PUT", "/api/users/me", Map.of("fullName", "X", "phone", "abc"), attendee(auth));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        Response auth = register(uniqueEmail());

        Response response = send("PUT", "/api/users/me/password",
                Map.of("currentPassword", "Wrong-pass1", "newPassword", "New-Passw0rd"), attendee(auth));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("WRONG_PASSWORD");
    }

    @Test
    void changePasswordSwitchesCredentialsAndRevokesSessions() {
        String email = uniqueEmail();
        Response auth = register(email);

        Response response = send("PUT", "/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "New-Passw0rd"), attendee(auth));

        assertThat(response.status()).isEqualTo(204);
        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(login(email, "New-Passw0rd").status()).isEqualTo(200);
        assertThat(refresh(auth.body().path("refreshToken").asString()).status()).isEqualTo(401);
    }

    @Test
    void newPasswordIsValidated() {
        Response auth = register(uniqueEmail());

        Response response = send("PUT", "/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "short"), attendee(auth));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void nonAdminCannotUseAdminApis() {
        Response auth = register(uniqueEmail());
        Caller attendee = attendee(auth);

        Response list = send("GET", "/api/users", null, attendee);
        assertThat(list.status()).isEqualTo(403);
        assertThat(list.code()).isEqualTo("FORBIDDEN");
        assertThat(send("GET", "/api/users/" + attendee.id(), null, attendee).status()).isEqualTo(403);
        assertThat(send("PUT", "/api/users/" + attendee.id() + "/lock", null, attendee).status()).isEqualTo(403);
        assertThat(send("PUT", "/api/users/" + attendee.id() + "/unlock", null, attendee).status()).isEqualTo(403);
    }

    @Test
    void adminCanSearchByKeywordCaseInsensitively() {
        String marker = "Zqx" + UUID.randomUUID().toString().substring(0, 8);
        String email = marker.toLowerCase() + "@example.com";
        register(email);

        Response byEmail = send("GET", "/api/users?keyword=" + marker.toUpperCase(), null, admin());

        assertThat(byEmail.status()).isEqualTo(200);
        assertThat(byEmail.body().path("totalElements").asInt()).isEqualTo(1);
        assertThat(byEmail.body().path("content").get(0).path("email").asString()).isEqualTo(email);
        assertThat(byEmail.raw()).doesNotContainIgnoringCase("password");
    }

    @Test
    void keywordWildcardCharactersAreTreatedLiterally() {
        Response response = send("GET", "/api/users?keyword=%25%25", null, admin());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("totalElements").asInt()).isZero();
    }

    @Test
    void adminCanFilterByStatusAndPaginate() {
        Caller admin = admin();
        String lockedId = register(uniqueEmail()).body().path("user").path("id").asString();
        send("PUT", "/api/users/" + lockedId + "/lock", null, admin);
        register(uniqueEmail());
        register(uniqueEmail());

        Response locked = send("GET", "/api/users?status=LOCKED&size=100", null, admin);
        assertThat(locked.status()).isEqualTo(200);
        assertThat(locked.raw()).contains(lockedId);
        locked.body().path("content").forEach(user ->
                assertThat(user.path("status").asString()).isEqualTo("LOCKED"));

        Response page = send("GET", "/api/users?page=0&size=2", null, admin);
        assertThat(page.body().path("content").size()).isEqualTo(2);
        assertThat(page.body().path("size").asInt()).isEqualTo(2);
        assertThat(page.body().path("totalElements").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void invalidListParametersAreBadRequest() {
        Caller admin = admin();

        assertThat(send("GET", "/api/users?status=NOPE", null, admin).status()).isEqualTo(400);
        assertThat(send("GET", "/api/users?size=101", null, admin).status()).isEqualTo(400);
        assertThat(send("GET", "/api/users?size=0", null, admin).status()).isEqualTo(400);
        assertThat(send("GET", "/api/users?page=-1", null, admin).status()).isEqualTo(400);
    }

    @Test
    void adminCanGetUserById() {
        String email = uniqueEmail();
        String id = register(email).body().path("user").path("id").asString();

        Response user = send("GET", "/api/users/" + id, null, admin());

        assertThat(user.status()).isEqualTo(200);
        assertThat(user.body().path("email").asString()).isEqualTo(email);
    }

    @Test
    void unknownOrMalformedUserIdIsHandled() {
        Caller admin = admin();

        Response missing = send("GET", "/api/users/" + UUID.randomUUID(), null, admin);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.code()).isEqualTo("USER_NOT_FOUND");
        assertThat(send("GET", "/api/users/not-a-uuid", null, admin).status()).isEqualTo(400);
        assertThat(send("PUT", "/api/users/" + UUID.randomUUID() + "/lock", null, admin).status()).isEqualTo(404);
    }

    @Test
    void adminCannotLockOwnAccount() {
        Caller admin = admin();

        Response response = send("PUT", "/api/users/" + admin.id() + "/lock", null, admin);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("CANNOT_LOCK_SELF");
    }

    @Test
    void wrongMethodReturnsJson405() {
        Response response = send("DELETE", "/api/users/me", null, new Caller(UUID.randomUUID().toString(), "ATTENDEE"));

        assertThat(response.status()).isEqualTo(405);
        assertThat(response.body().path("status").asInt()).isEqualTo(405);
    }
}
