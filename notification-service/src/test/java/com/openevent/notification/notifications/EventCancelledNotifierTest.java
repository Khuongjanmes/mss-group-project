package com.openevent.notification.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserBatch;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.mail.EmailSender;

class EventCancelledNotifierTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final EventClient events = mock(EventClient.class);
    private final EmailSender email = mock(EmailSender.class);
    private final EventCancelledNotifier notifier = new EventCancelledNotifier(notifications, identity, events, email,
            "https://openevent.test");

    private final UUID eventId = UUID.randomUUID();
    private final UUID an = UUID.randomUUID();
    private final UUID binh = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-12-20T12:00:00Z");

    @BeforeEach
    void stubLookups() {
        when(events.getEvent(eventId)).thenReturn(new EventSummary(eventId, "Hội thảo AI", start,
                start.plusSeconds(3 * 3600), "Hội trường A", "1 Lê Duẩn", "Huế"));
        when(identity.getUsers(any())).thenReturn(List.of(contact(an, "an@example.com", "Nguyễn An"),
                contact(binh, "binh@example.com", "Trần Bình")));
        when(email.enabled()).thenReturn(true);
    }

    @Test
    void emailsEachOwnerOnceAndStoresNotifications() {
        TicketsCancelled message = cancelled(an, binh, an);

        notifier.handle(message);

        ArgumentCaptor<UserBatch> batch = ArgumentCaptor.forClass(UserBatch.class);
        verify(identity).getUsers(batch.capture());
        assertThat(batch.getValue().ids()).containsExactly(an, binh);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("an@example.com"), eq("Sự kiện đã bị hủy: Hội thảo AI"), html.capture(), anyMap());
        verify(email).send(eq("binh@example.com"), eq("Sự kiện đã bị hủy: Hội thảo AI"), anyString(), anyMap());
        assertThat(html.getValue()).contains("Nguyễn An", "Hội thảo AI", "ĐÃ HỦY", "19:00 – 22:00, Chủ nhật, 20/12/2026",
                "Hội trường A", "Hoàn tiền", "href=\"https://openevent.test/orders\"");

        List<Notification> saved = savedNotifications(2);
        assertThat(saved).extracting(Notification::getUserId).containsExactly(an, binh);
        assertThat(saved).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.EVENT_CANCELLED);
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(n.getMessageId()).isEqualTo(message.messageId());
            assertThat(n.getContent()).contains("Hội thảo AI", "đã bị hủy", "hoàn tiền");
        });
    }

    @Test
    void retryAfterPartialFailureOnlyEmailsThoseNotYetNotified() {
        TicketsCancelled message = cancelled(an, binh);
        doThrow(new MailSendException("SMTP down")).when(email)
                .send(eq("binh@example.com"), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> notifier.handle(message)).isInstanceOf(MailSendException.class);
        assertThat(savedNotifications(1)).extracting(Notification::getUserId).containsExactly(an);

        // Lần thử lại: An đã có thông báo, chỉ còn Bình.
        when(notifications.findByMessageId(message.messageId())).thenReturn(List.of(notified(message, an)));
        doThrow(new RuntimeException("không gửi lại cho An")).when(email)
                .send(eq("an@example.com"), anyString(), anyString(), anyMap());
        org.mockito.Mockito.doNothing().when(email).send(eq("binh@example.com"), anyString(), anyString(), anyMap());

        notifier.handle(message);

        verify(email, times(2)).send(eq("binh@example.com"), anyString(), anyString(), anyMap());
        verify(email, times(1)).send(eq("an@example.com"), anyString(), anyString(), anyMap());
    }

    @Test
    void messageAlreadyHandledForEveryoneIsSkipped() {
        TicketsCancelled message = cancelled(an, binh);
        when(notifications.findByMessageId(message.messageId()))
                .thenReturn(List.of(notified(message, an), notified(message, binh)));

        notifier.handle(message);

        verifyNoInteractions(events, identity);
        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
        verify(notifications, never()).save(any());
    }

    @Test
    void ownerWithoutAccountIsSkippedWithoutFailing() {
        UUID gone = UUID.randomUUID();

        notifier.handle(cancelled(an, gone));

        verify(email).send(eq("an@example.com"), anyString(), anyString(), anyMap());
        assertThat(savedNotifications(1)).extracting(Notification::getUserId).containsExactly(an);
    }

    @Test
    void withoutMailServerOnlyStoresInAppNotifications() {
        when(email.enabled()).thenReturn(false);

        notifier.handle(cancelled(an, binh));

        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
        assertThat(savedNotifications(2)).extracting(Notification::getChannel)
                .containsOnly(NotificationChannel.IN_APP);
    }

    @Test
    void largeEventsAskIdentityInBatchesOfFiveHundred() {
        UUID[] owners = IntStream.range(0, 501).mapToObj(i -> UUID.randomUUID()).toArray(UUID[]::new);
        when(identity.getUsers(any())).thenReturn(List.of());

        notifier.handle(cancelled(owners));

        ArgumentCaptor<UserBatch> batches = ArgumentCaptor.forClass(UserBatch.class);
        verify(identity, times(2)).getUsers(batches.capture());
        assertThat(batches.getAllValues()).extracting(b -> b.ids().size()).containsExactly(500, 1);
    }

    private TicketsCancelled cancelled(UUID... owners) {
        return new TicketsCancelled(UUID.randomUUID(), Instant.now(), eventId, List.of(owners));
    }

    private static Notification notified(TicketsCancelled message, UUID userId) {
        return Notification.sent(message.messageId(), userId, NotificationType.EVENT_CANCELLED, "t", "c",
                NotificationChannel.EMAIL, Instant.now());
    }

    private static UserContact contact(UUID id, String email, String name) {
        return new UserContact(id, email, name, null, "ACTIVE");
    }

    private List<Notification> savedNotifications(int count) {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(count)).save(saved.capture());
        return saved.getAllValues();
    }
}
