package com.openevent.notification.notifications;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.mail.EmailSender;
import com.openevent.notification.mail.QrCodes;

/**
 * KB-4 bước 4: vé đã phát hành thì gửi email kèm QR cho người mua và lưu thông báo.
 * <p>
 * BR-11: message đã xử lý (có thông báo cùng messageId cho người này) thì bỏ qua. Identity, Event hoặc máy chủ
 * mail lỗi thì ném lỗi để RabbitMQ thử lại rồi chuyển vào dead-letter queue; chưa lưu gì nên lần thử lại gửi đủ.
 */
@Service
public class TicketIssuedNotifier {

    private static final Logger log = LoggerFactory.getLogger(TicketIssuedNotifier.class);

    private final NotificationRepository notifications;
    private final IdentityClient identity;
    private final EventClient events;
    private final EmailSender email;
    private final String frontendUrl;

    public TicketIssuedNotifier(NotificationRepository notifications, IdentityClient identity, EventClient events,
            EmailSender email, @Value("${openevent.frontend-url:}") String frontendUrl) {
        this.notifications = notifications;
        this.identity = identity;
        this.events = events;
        this.email = email;
        this.frontendUrl = frontendUrl;
    }

    public void handle(TicketIssued message) {
        if (notifications.existsByMessageIdAndUserId(message.messageId(), message.ownerId())) {
            log.info("Bỏ qua TicketIssued {} đã xử lý", message.messageId());
            return;
        }
        UserContact owner = identity.getUser(message.ownerId());
        EventSummary event = events.getEvent(message.eventId());
        List<String> codes = message.ticketCodes();

        String title = "Vé của bạn: " + event.title();
        String content = "%d vé · %s · %s".formatted(codes.size(),
                TicketEmailTemplate.when(event.startTime(), event.endTime()), TicketEmailTemplate.venue(event));

        NotificationChannel channel = NotificationChannel.IN_APP;
        if (email.enabled()) {
            Map<String, byte[]> qrImages = new LinkedHashMap<>();
            for (int i = 0; i < codes.size(); i++) {
                qrImages.put(qrId(i), QrCodes.png(codes.get(i)));
            }
            String html = TicketEmailTemplate.html(owner.fullName(), event, message.orderId(), codes, frontendUrl,
                    TicketIssuedNotifier::qrId);
            email.send(owner.email(), title, html, qrImages);
            channel = NotificationChannel.EMAIL;
        } else {
            log.warn("Chưa cấu hình MAIL_HOST: không gửi email vé cho đơn {}, chỉ lưu thông báo", message.orderId());
        }

        try {
            notifications.save(Notification.sent(message.messageId(), message.ownerId(), NotificationType.TICKET_ISSUED,
                    title, content, channel, Instant.now()));
        } catch (DuplicateKeyException e) {
            // Hai lần nhận cùng message chạy song song: lần kia đã lưu.
            log.info("TicketIssued {} đã được lưu bởi lần xử lý khác", message.messageId());
        }
    }

    private static String qrId(int index) {
        return "ticket-qr-" + index;
    }
}
