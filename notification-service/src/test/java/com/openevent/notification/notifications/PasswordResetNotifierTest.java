package com.openevent.notification.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.openevent.common.messaging.events.PasswordResetRequested;
import com.openevent.notification.mail.EmailSender;

class PasswordResetNotifierTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final EmailSender email = mock(EmailSender.class);
    private final PasswordResetNotifier notifier = new PasswordResetNotifier(notifications, email, "https://openevent.test/");

    // 19:30 giờ Việt Nam.
    private final PasswordResetRequested message = new PasswordResetRequested(UUID.randomUUID(), Instant.now(),
            UUID.randomUUID(), "an@example.com", "Nguyễn An", "abc-_123", Instant.parse("2026-12-20T12:30:00Z"));

    @Test
    void emailsTheResetLinkAndStoresANotificationWithoutTheToken() {
        when(email.enabled()).thenReturn(true);

        notifier.handle(message);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("an@example.com"), eq("Đặt lại mật khẩu OpenEvent"), html.capture(), any());
        assertThat(html.getValue()).contains("Nguyễn An", "href=\"https://openevent.test/reset-password?token=abc-_123\"",
                "19:30 20/12/2026");
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(saved.getValue().getContent()).doesNotContain("abc-_123");
    }

    @Test
    void duplicateMessageSendsNothing() {
        when(email.enabled()).thenReturn(true);
        when(notifications.existsByMessageIdAndUserId(message.messageId(), message.userId())).thenReturn(true);

        notifier.handle(message);

        verify(email, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    void withoutMailSettingsNothingIsSentOrStored() {
        when(email.enabled()).thenReturn(false);

        notifier.handle(message);

        verify(email, never()).send(anyString(), anyString(), anyString(), any());
        verify(notifications, never()).save(any());
    }
}
