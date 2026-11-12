package com.openevent.notification.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.mail.EmailSender;

class TicketIssuedNotifierTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final EventClient events = mock(EventClient.class);
    private final EmailSender email = mock(EmailSender.class);
    private final TicketIssuedNotifier notifier = new TicketIssuedNotifier(notifications, identity, events, email);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    // 19:00 giờ Việt Nam.
    private final Instant start = Instant.parse("2026-12-20T12:00:00Z");
    private final TicketIssued message = new TicketIssued(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
            ownerId, eventId, List.of("code-A", "code-B"));

    @BeforeEach
    void stubLookups() {
        when(identity.getUser(ownerId))
                .thenReturn(new UserContact(ownerId, "an@example.com", "Nguyễn An", null, "ACTIVE"));
        when(events.getEvent(eventId)).thenReturn(new EventSummary(eventId, "Hội thảo AI", start,
                start.plusSeconds(3 * 3600), "Hội trường A", "1 Lê Duẩn", "Huế"));
    }

    @Test
    void emailsOneQrPerTicketAndStoresEmailNotification() {
        when(email.enabled()).thenReturn(true);

        notifier.handle(message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, byte[]>> images = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).send(org.mockito.ArgumentMatchers.eq("an@example.com"),
                org.mockito.ArgumentMatchers.eq("Vé của bạn: Hội thảo AI"), html.capture(), images.capture());
        assertThat(images.getValue()).containsOnlyKeys("ticket-qr-0", "ticket-qr-1");
        assertThat(images.getValue().values()).allSatisfy(png -> assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'));
        assertThat(html.getValue()).contains("cid:ticket-qr-0", "cid:ticket-qr-1", "code-A", "code-B",
                "19:00 ngày 20/12/2026", "Hội trường A, 1 Lê Duẩn, Huế");

        Notification saved = savedNotification();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getType()).isEqualTo(NotificationType.TICKET_ISSUED);
        assertThat(saved.getUserId()).isEqualTo(ownerId);
        assertThat(saved.getMessageId()).isEqualTo(message.messageId());
        assertThat(saved.getTitle()).isEqualTo("Vé của bạn: Hội thảo AI");
        assertThat(saved.getContent()).contains("2 vé", "Hội thảo AI");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    void alreadyHandledMessageIsSkipped() {
        when(notifications.existsByMessageIdAndUserId(message.messageId(), ownerId)).thenReturn(true);

        notifier.handle(message);

        verifyNoInteractions(identity, events, email);
        verify(notifications, never()).save(any());
    }

    @Test
    void withoutMailServerOnlyStoresInAppNotification() {
        when(email.enabled()).thenReturn(false);

        notifier.handle(message);

        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
        assertThat(savedNotification().getChannel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void mailFailureStoresNothingSoTheRetrySendsAgain() {
        when(email.enabled()).thenReturn(true);
        doThrow(new MailSendException("SMTP down")).when(email).send(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> notifier.handle(message)).isInstanceOf(MailSendException.class);

        verify(notifications, never()).save(any());
    }

    @Test
    void eventTitleIsEscapedInHtml() {
        when(email.enabled()).thenReturn(true);
        when(events.getEvent(eventId)).thenReturn(new EventSummary(eventId, "<script>x</script>", start,
                start.plusSeconds(3600), "A", "B", "C"));

        notifier.handle(message);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).send(anyString(), anyString(), html.capture(), anyMap());
        assertThat(html.getValue()).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        return saved.getValue();
    }
}
