package com.openevent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.PasswordResetRequested;
import com.openevent.identity.support.IdentityIntegrationTest;

/** Quên mật khẩu: không lộ email nào có tài khoản; token dùng một lần, có hạn; đặt lại thu hồi mọi phiên. */
class PasswordResetIT extends IdentityIntegrationTest {

    private static final String NEW_PASSWORD = "N3w-Passw0rd!";

    @BeforeEach
    void forgetEarlierMessages() {
        clearInvocations(rabbit);
    }

    private Response forgot(String email) {
        return post("/api/auth/forgot-password", Map.of("email", email));
    }

    private Response reset(String token, String password) {
        return post("/api/auth/reset-password", Map.of("token", token, "newPassword", password));
    }

    /** Token gốc nằm trong message gửi Notification. */
    private PasswordResetRequested sentMessage() {
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(rabbit, timeout(2000)).convertAndSend(eq(Messaging.EXCHANGE), eq(Messaging.PASSWORD_RESET_REQUESTED),
                message.capture());
        return (PasswordResetRequested) message.getValue();
    }

    @Test
    void resetLinkChangesThePasswordOnceAndSignsOutEverywhere() {
        String email = uniqueEmail();
        String oldRefresh = register(email).body().path("refreshToken").asString();

        assertThat(forgot(email.toUpperCase()).status()).isEqualTo(202);
        PasswordResetRequested message = sentMessage();
        assertThat(message.email()).isEqualTo(email);
        assertThat(message.fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = ?::uuid",
                String.class, message.userId().toString())).isNotEqualTo(message.token());

        assertThat(reset(message.token(), "short").status()).isEqualTo(400);
        assertThat(reset(message.token(), NEW_PASSWORD).status()).isEqualTo(204);
        assertThat(login(email, NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(refresh(oldRefresh).status()).isEqualTo(401);
        assertThat(reset(message.token(), "An0ther-Passw0rd").code()).isEqualTo("RESET_TOKEN_INVALID");
    }

    @Test
    void unknownEmailLooksTheSameAndSendsNothing() {
        Response response = forgot("khong-co-ai-" + uniqueEmail());

        assertThat(response.status()).isEqualTo(202);
        verify(rabbit, never()).convertAndSend(eq(Messaging.EXCHANGE), eq(Messaging.PASSWORD_RESET_REQUESTED), any(Object.class));
    }

    @Test
    void newRequestReplacesTheOldLinkAndExpiredLinksFail() {
        String email = uniqueEmail();
        register(email);
        forgot(email);
        String first = sentMessage().token();
        clearInvocations(rabbit);
        forgot(email);
        String second = sentMessage().token();

        assertThat(reset(first, NEW_PASSWORD).code()).isEqualTo("RESET_TOKEN_INVALID");

        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 minute' "
                + "where user_id = (select id from users where email = ?)", email);
        assertThat(reset(second, NEW_PASSWORD).code()).isEqualTo("RESET_TOKEN_INVALID");
        assertThat(reset("khong-phai-token", NEW_PASSWORD).code()).isEqualTo("RESET_TOKEN_INVALID");
    }
}
