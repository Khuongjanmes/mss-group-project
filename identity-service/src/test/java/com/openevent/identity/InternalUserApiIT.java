package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.openevent.identity.support.IdentityIntegrationTest;

/** API cho Feign (KB-1, Notification, Ticket): không cần danh tính, Gateway chặn từ bên ngoài. */
class InternalUserApiIT extends IdentityIntegrationTest {

    @Test
    void returnsContactAndStatusWithoutCredentials() {
        String email = uniqueEmail();
        String id = register(email).body().path("user").path("id").asString();

        Response user = send("GET", "/internal/users/" + id, null, null);

        assertThat(user.status()).isEqualTo(200);
        assertThat(user.body().path("id").asString()).isEqualTo(id);
        assertThat(user.body().path("email").asString()).isEqualTo(email);
        assertThat(user.body().path("fullName").asString()).isEqualTo("Nguyễn Văn A");
        assertThat(user.body().path("status").asString()).isEqualTo("ACTIVE");
        assertThat(user.raw()).doesNotContainIgnoringCase("password");
    }

    @Test
    void reflectsLockedStatusForBr12() {
        String id = register(uniqueEmail()).body().path("user").path("id").asString();
        send("PUT", "/api/users/" + id + "/lock", null, admin());

        assertThat(send("GET", "/internal/users/" + id, null, null).body().path("status").asString()).isEqualTo("LOCKED");
    }

    @Test
    void unknownUserIs404() {
        assertThat(send("GET", "/internal/users/" + UUID.randomUUID(), null, null).status()).isEqualTo(404);
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        String email = uniqueEmail();
        String id = register(email).body().path("user").path("id").asString();

        Response found = send("GET", "/internal/users/by-email?email=" + email.toUpperCase(Locale.ROOT), null, null);

        assertThat(found.status()).isEqualTo(200);
        assertThat(found.body().path("id").asString()).isEqualTo(id);
        assertThat(send("GET", "/internal/users/by-email?email=" + uniqueEmail(), null, null).status()).isEqualTo(404);
    }

    @Test
    void batchReturnsOnlyExistingUsers() {
        String first = register(uniqueEmail()).body().path("user").path("id").asString();
        String second = register(uniqueEmail()).body().path("user").path("id").asString();

        Response batch = post("/internal/users/batch", Map.of("ids", List.of(first, second, UUID.randomUUID().toString())));

        assertThat(batch.status()).isEqualTo(200);
        assertThat(batch.body().size()).isEqualTo(2);
        assertThat(batch.raw()).contains(first, second);
    }

    @Test
    void batchRejectsEmptyAndOversizedRequests() {
        assertThat(post("/internal/users/batch", Map.of("ids", List.of())).status()).isEqualTo(400);
        List<String> tooMany = Collections.nCopies(501, UUID.randomUUID().toString());
        assertThat(post("/internal/users/batch", Map.of("ids", tooMany)).status()).isEqualTo(400);
    }
}
