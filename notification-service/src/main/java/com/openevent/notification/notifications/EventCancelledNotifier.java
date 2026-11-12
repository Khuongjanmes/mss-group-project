package com.openevent.notification.notifications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserBatch;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.mail.EmailSender;

/**
 * KB-5 bước 2: sự kiện bị hủy thì gửi email cho từng người có vé và lưu thông báo.
 * <p>
 * BR-11 theo từng người: người đã có thông báo của message này thì bỏ qua. Mỗi người được gửi rồi lưu ngay, nên
 * lỗi giữa chừng (SMTP, Identity) làm message được thử lại mà người đã nhận không bị gửi lần hai.
 */
@Service
public class EventCancelledNotifier {

    private static final Logger log = LoggerFactory.getLogger(EventCancelledNotifier.class);

    private final NotificationRepository notifications;
    private final IdentityClient identity;
    private final EventClient events;
    private final EmailSender email;
    private final String frontendUrl;

    public EventCancelledNotifier(NotificationRepository notifications, IdentityClient identity, EventClient events,
            EmailSender email, @Value("${openevent.frontend-url:}") String frontendUrl) {
        this.notifications = notifications;
        this.identity = identity;
        this.events = events;
        this.email = email;
        this.frontendUrl = frontendUrl;
    }

    public void handle(TicketsCancelled message) {
        Set<UUID> notified = notifications.findByMessageId(message.messageId()).stream()
                .map(Notification::getUserId)
                .collect(Collectors.toSet());
        List<UUID> pending = new ArrayList<>(new LinkedHashSet<>(message.ownerIds()));
        pending.removeAll(notified);
        if (pending.isEmpty()) {
            log.info("Bỏ qua TicketsCancelled {}: mọi người đã được báo", message.messageId());
            return;
        }

        EventSummary event = events.getEvent(message.eventId());
        Map<UUID, UserContact> contacts = contacts(pending);
        String title = EventCancelledEmailTemplate.subject(event);
        String content = EventCancelledEmailTemplate.summary(event);
        if (!email.enabled()) {
            log.warn("Chưa cấu hình MAIL_HOST: không gửi email báo hủy sự kiện {}, chỉ lưu thông báo", message.eventId());
        }

        for (UUID userId : pending) {
            UserContact user = contacts.get(userId);
            if (user == null) {
                // Tài khoản không còn: không có ai để báo, cũng không cần thử lại.
                log.warn("Không tìm thấy người dùng {} khi báo hủy sự kiện {}", userId, message.eventId());
                continue;
            }
            NotificationChannel channel = NotificationChannel.IN_APP;
            if (email.enabled()) {
                email.send(user.email(), title, EventCancelledEmailTemplate.html(user.fullName(), event, frontendUrl),
                        Map.of());
                channel = NotificationChannel.EMAIL;
            }
            try {
                notifications.save(Notification.sent(message.messageId(), userId, NotificationType.EVENT_CANCELLED,
                        title, content, channel, Instant.now()));
            } catch (DuplicateKeyException e) {
                log.info("TicketsCancelled {} đã được lưu cho {} bởi lần xử lý khác", message.messageId(), userId);
            }
        }
    }

    private Map<UUID, UserContact> contacts(List<UUID> ids) {
        Map<UUID, UserContact> contacts = new HashMap<>();
        for (int from = 0; from < ids.size(); from += IdentityClient.MAX_BATCH) {
            List<UUID> batch = ids.subList(from, Math.min(from + IdentityClient.MAX_BATCH, ids.size()));
            for (UserContact contact : identity.getUsers(new UserBatch(batch))) {
                contacts.put(contact.id(), contact);
            }
        }
        return contacts;
    }
}
