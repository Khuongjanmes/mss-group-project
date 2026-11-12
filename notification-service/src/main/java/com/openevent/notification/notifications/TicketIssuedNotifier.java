package com.openevent.notification.notifications;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.notification.client.EventClient;
import com.openevent.notification.client.EventClient.EventSummary;
import com.openevent.notification.client.IdentityClient;
import com.openevent.notification.client.IdentityClient.UserContact;
import com.openevent.notification.mail.EmailSender;
import com.openevent.notification.mail.QrCodes;

import lombok.RequiredArgsConstructor;

/**
 * KB-4 bước 4: vé đã phát hành thì gửi email kèm QR cho người mua và lưu thông báo.
 * <p>
 * BR-11: message đã xử lý (có thông báo cùng messageId cho người này) thì bỏ qua. Identity, Event hoặc máy chủ
 * mail lỗi thì ném lỗi để RabbitMQ thử lại rồi chuyển vào dead-letter queue; chưa lưu gì nên lần thử lại gửi đủ.
 */
@Service
@RequiredArgsConstructor
public class TicketIssuedNotifier {

    private static final Logger log = LoggerFactory.getLogger(TicketIssuedNotifier.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(VIETNAM);

    private final NotificationRepository notifications;
    private final IdentityClient identity;
    private final EventClient events;
    private final EmailSender email;

    public void handle(TicketIssued message) {
        if (notifications.existsByMessageIdAndUserId(message.messageId(), message.ownerId())) {
            log.info("Bỏ qua TicketIssued {} đã xử lý", message.messageId());
            return;
        }
        UserContact owner = identity.getUser(message.ownerId());
        EventSummary event = events.getEvent(message.eventId());
        List<String> codes = message.ticketCodes();

        String title = "Vé của bạn: " + event.title();
        String content = "Đã phát hành %d vé cho sự kiện \"%s\" lúc %s tại %s.".formatted(codes.size(), event.title(),
                TIME.format(event.startTime()), venue(event));

        NotificationChannel channel = NotificationChannel.IN_APP;
        if (email.enabled()) {
            Map<String, byte[]> qrImages = new LinkedHashMap<>();
            for (int i = 0; i < codes.size(); i++) {
                qrImages.put(qrId(i), QrCodes.png(codes.get(i)));
            }
            email.send(owner.email(), title, html(owner, event, codes), qrImages);
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

    private static String venue(EventSummary event) {
        return "%s, %s, %s".formatted(event.venueName(), event.venueAddress(), event.venueCity());
    }

    private static String qrId(int index) {
        return "ticket-qr-" + index;
    }

    private static String html(UserContact owner, EventSummary event, List<String> codes) {
        StringBuilder tickets = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            tickets.append("""
                    <div style="display:inline-block;margin:8px;padding:12px;border:1px solid #ddd;border-radius:8px;text-align:center">
                      <img src="cid:%s" width="180" height="180" alt="QR vé %d"><br>
                      <code style="font-size:13px">%s</code>
                    </div>
                    """.formatted(qrId(i), i + 1, escape(codes.get(i))));
        }
        return """
                <div style="font-family:Arial,sans-serif;color:#222;max-width:640px">
                  <p>Xin chào %s,</p>
                  <p>Thanh toán của bạn đã thành công. Dưới đây là <b>%d vé</b> cho sự kiện:</p>
                  <h2 style="margin:8px 0">%s</h2>
                  <p>Thời gian: <b>%s</b> đến <b>%s</b><br>Địa điểm: <b>%s</b></p>
                  <p>Đưa mã QR cho nhân viên quét khi vào cửa. Mỗi mã chỉ dùng được một lần, không chia sẻ cho người khác.</p>
                  %s
                  <p style="color:#777;font-size:12px">Vé cũng có ở trang "Vé của tôi" trên OpenEvent.</p>
                </div>
                """.formatted(escape(owner.fullName()), codes.size(), escape(event.title()),
                TIME.format(event.startTime()), TIME.format(event.endTime()), escape(venue(event)), tickets);
    }

    private static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
