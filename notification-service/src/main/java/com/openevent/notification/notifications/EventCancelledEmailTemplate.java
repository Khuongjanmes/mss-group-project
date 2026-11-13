package com.openevent.notification.notifications;

import com.openevent.notification.client.EventClient.EventSummary;

/** Email báo sự kiện bị hủy (KB-5): sự kiện nào, vé không còn dùng được, đơn đã thanh toán được hoàn tiền. */
final class EventCancelledEmailTemplate {

    private static final String BADGE = "<span style=\"display:inline-block;margin-left:8px;padding:2px 8px;"
            + "border-radius:6px;background:" + EmailLayout.DANGER_BG + ";color:" + EmailLayout.DANGER
            + ";font-size:12px;font-weight:700;vertical-align:middle\">ĐÃ HỦY</span>";

    private EventCancelledEmailTemplate() {
    }

    static String subject(EventSummary event) {
        return "Sự kiện đã bị hủy: " + event.title();
    }

    /** Nội dung ngắn lưu vào thông báo trong ứng dụng. */
    static String summary(EventSummary event) {
        return "%s (%s) đã bị hủy. Vé không còn dùng được; đơn đã thanh toán sẽ được hoàn tiền.".formatted(event.title(),
                EmailLayout.when(event.startTime(), event.endTime()));
    }

    /** @param frontendUrl địa chỉ SPA (FRONTEND_URL); trống thì không có nút dẫn về trang web */
    static String html(String fullName, EventSummary event, String frontendUrl) {
        StringBuilder html = EmailLayout.begin(subject(event),
                event.title() + " (" + EmailLayout.when(event.startTime(), event.endTime()) + ") đã bị hủy",
                "Thông báo hủy sự kiện");

        EmailLayout.intro(html, fullName,
                "Rất tiếc, ban tổ chức đã hủy sự kiện dưới đây. Vé của bạn cho sự kiện này không còn dùng được.",
                event.title(), BADGE);
        EmailLayout.eventDetails(html, event);
        EmailLayout.buttons(html, frontendUrl, "/orders", "Xem đơn của tôi", "/events", "Tìm sự kiện khác");
        EmailLayout.endSection(html);

        EmailLayout.notice(html, EmailLayout.DANGER_BG, "Hoàn tiền",
                "Đơn đã thanh toán cho sự kiện này được chuyển sang chờ hoàn tiền.",
                "Tiền được hoàn về tài khoản bạn đã dùng để thanh toán. Bạn không cần làm gì thêm.",
                "Đơn chưa thanh toán đã được hủy và không bị trừ tiền.");
        return EmailLayout.end(html,
                "Email được gửi tự động từ OpenEvent vì bạn có vé cho sự kiện này. Vui lòng không trả lời email này.");
    }
}
