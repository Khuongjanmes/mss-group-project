package com.openevent.notification.notifications;

import static com.openevent.notification.notifications.EmailLayout.BORDER;
import static com.openevent.notification.notifications.EmailLayout.MUTED;
import static com.openevent.notification.notifications.EmailLayout.PRIMARY_DARK;
import static com.openevent.notification.notifications.EmailLayout.TEXT;
import static com.openevent.notification.notifications.EmailLayout.e;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;

import com.openevent.notification.client.EventClient.EventSummary;

/** Email vé (KB-4): thông tin sự kiện, mỗi vé một thẻ kèm QR nhúng, hướng dẫn vào cửa. */
final class TicketEmailTemplate {

    private TicketEmailTemplate() {
    }

    static String when(Instant start, Instant end) {
        return EmailLayout.when(start, end);
    }

    static String venue(EventSummary event) {
        return EmailLayout.venue(event);
    }

    /**
     * @param frontendUrl địa chỉ SPA (FRONTEND_URL); trống thì không có nút dẫn về trang web
     * @param qrId content-id của ảnh QR thứ i, khớp với ảnh nhúng trong email
     */
    static String html(String fullName, EventSummary event, UUID orderId, List<String> codes, String frontendUrl,
            IntFunction<String> qrId) {
        int count = codes.size();
        StringBuilder html = EmailLayout.begin("Vé của bạn: " + event.title(),
                count + " vé cho " + event.title() + " · " + when(event.startTime(), event.endTime()),
                "Thanh toán thành công · " + count + " vé");

        EmailLayout.intro(html, fullName, "Vé của bạn đã sẵn sàng. Mang theo mã QR bên dưới khi đến sự kiện.",
                event.title(), "");
        EmailLayout.eventDetails(html, event, "Mã đơn", "<span style=\"font-family:Consolas,Menlo,monospace\">"
                + e(orderId.toString().substring(0, 8).toUpperCase()) + "</span>");
        EmailLayout.buttons(html, frontendUrl, "/tickets", "Xem vé của tôi", "/events/" + event.id(),
                "Chi tiết sự kiện");
        EmailLayout.endSection(html);

        // Từng vé: số thứ tự, QR, mã vé.
        html.append("<tr><td style=\"padding:20px 28px 4px\">")
                .append("<div style=\"font-size:13px;font-weight:700;text-transform:uppercase;letter-spacing:.6px;color:")
                .append(MUTED).append(";margin-bottom:12px\">Vé của bạn</div>");
        for (int i = 0; i < count; i++) {
            html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                    .append("style=\"border:1px solid ").append(BORDER).append(";border-radius:10px;margin-bottom:12px\">")
                    .append("<tr><td align=\"center\" style=\"padding:20px 16px\">")
                    .append("<div style=\"font-size:14px;font-weight:700;color:").append(PRIMARY_DARK)
                    .append(";margin-bottom:12px\">Vé ").append(i + 1).append("/").append(count).append("</div>")
                    .append("<img src=\"cid:").append(qrId.apply(i)).append("\" width=\"200\" height=\"200\" alt=\"Mã QR vé ")
                    .append(i + 1).append("\" style=\"display:block;width:200px;height:200px;border:0\">")
                    .append("<div style=\"margin-top:12px;font-size:12px;color:").append(MUTED).append("\">Mã vé</div>")
                    .append("<div style=\"font-family:Consolas,Menlo,monospace;font-size:13px;color:").append(TEXT)
                    .append(";word-break:break-all\">").append(e(codes.get(i))).append("</div>")
                    .append("</td></tr></table>");
        }
        EmailLayout.endSection(html);

        EmailLayout.notice(html, EmailLayout.ACCENT_BG, "Khi đến sự kiện",
                "Mở email này hoặc trang Vé của tôi, đưa mã QR cho nhân viên quét.",
                "Mỗi mã chỉ dùng được một lần. Đi cùng bạn bè thì mỗi người một vé.",
                "Không đăng ảnh QR lên mạng xã hội để tránh bị người khác dùng trước.");
        return EmailLayout.end(html, "Email được gửi tự động từ OpenEvent vì bạn đã mua vé. Vui lòng không trả lời email này.");
    }
}
