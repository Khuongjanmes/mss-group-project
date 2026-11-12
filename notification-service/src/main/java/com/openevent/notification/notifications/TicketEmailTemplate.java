package com.openevent.notification.notifications;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;

import org.springframework.web.util.HtmlUtils;

import com.openevent.notification.client.EventClient.EventSummary;

/**
 * HTML của email vé. Bố cục bằng bảng và style inline vì Gmail, Outlook bỏ CSS ngoài và flexbox; màu lấy từ token
 * của frontend ({@code --primary} cam, {@code --foreground}). Chữ trắng trên nền cam luôn đậm và từ 14px.
 */
final class TicketEmailTemplate {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String PRIMARY = "#E8702A";
    private static final String PRIMARY_DARK = "#B4501A";
    private static final String ACCENT_BG = "#FFF4EC";
    private static final String TEXT = "#1F1D33";
    private static final String MUTED = "#6B6B80";
    private static final String BORDER = "#E5E7EB";
    private static final String PAGE_BG = "#F4F5F7";
    private static final String FONT = "'Be Vietnam Pro',Arial,Helvetica,sans-serif";

    private TicketEmailTemplate() {
    }

    /** "19:00 – 22:00, Chủ nhật, 20/12/2026"; khác ngày thì ghi đủ ngày cho cả hai đầu. */
    static String when(Instant start, Instant end) {
        ZonedDateTime from = start.atZone(VIETNAM);
        ZonedDateTime to = end.atZone(VIETNAM);
        if (from.toLocalDate().equals(to.toLocalDate())) {
            return HOUR.format(from) + " – " + HOUR.format(to) + ", " + day(from);
        }
        return HOUR.format(from) + ", " + day(from) + " – " + HOUR.format(to) + ", " + day(to);
    }

    static String venue(EventSummary event) {
        return event.venueName() + ", " + event.venueAddress() + ", " + event.venueCity();
    }

    /**
     * @param frontendUrl địa chỉ SPA (FRONTEND_URL); trống thì không có nút dẫn về trang web
     * @param qrId content-id của ảnh QR thứ i, khớp với ảnh nhúng trong email
     */
    static String html(String fullName, EventSummary event, UUID orderId, List<String> codes, String frontendUrl,
            IntFunction<String> qrId) {
        int count = codes.size();
        StringBuilder html = new StringBuilder(8_000);
        html.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(e("Vé của bạn: " + event.title())).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG).append("\">");

        // Dòng xem trước trong hộp thư, không hiện trong nội dung.
        html.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0\">")
                .append(e(count + " vé cho " + event.title() + " · " + when(event.startTime(), event.endTime())))
                .append("</div>");

        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(PAGE_BG).append("\"><tr><td align=\"center\" style=\"padding:24px 12px\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"width:600px;max-width:100%;background:#FFFFFF;border:1px solid ").append(BORDER)
                .append(";border-radius:12px;overflow:hidden;font-family:").append(FONT).append(";color:")
                .append(TEXT).append("\">");

        // Dải tiêu đề.
        html.append("<tr><td style=\"background:").append(PRIMARY).append(";padding:20px 28px\">")
                .append("<div style=\"font-size:20px;font-weight:700;color:#FFFFFF;letter-spacing:.2px\">OpenEvent</div>")
                .append("<div style=\"font-size:14px;font-weight:600;color:#FFFFFF;margin-top:4px\">")
                .append("Thanh toán thành công · ").append(count).append(" vé</div></td></tr>");

        // Lời chào và thông tin sự kiện.
        html.append("<tr><td style=\"padding:28px 28px 8px\">")
                .append("<p style=\"margin:0 0 8px;font-size:15px\">Xin chào <b>").append(e(fullName)).append("</b>,</p>")
                .append("<p style=\"margin:0 0 20px;font-size:15px;line-height:1.6;color:").append(MUTED)
                .append("\">Vé của bạn đã sẵn sàng. Mang theo mã QR bên dưới khi đến sự kiện.</p>")
                .append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:").append(TEXT).append("\">")
                .append(e(event.title())).append("</h1>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"font-size:14px;line-height:1.5\">");
        row(html, "Thời gian", e(when(event.startTime(), event.endTime())));
        row(html, "Địa điểm", "<b>" + e(event.venueName()) + "</b><br>" + e(event.venueAddress() + ", " + event.venueCity()));
        row(html, "Mã đơn", "<span style=\"font-family:Consolas,Menlo,monospace\">"
                + e(orderId.toString().substring(0, 8).toUpperCase()) + "</span>");
        html.append("</table>");

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 4px\"><tr>")
                    .append("<td style=\"background:").append(PRIMARY).append(";border-radius:8px\">")
                    .append("<a href=\"").append(e(base + "/tickets")).append("\" style=\"display:inline-block;")
                    .append("padding:12px 22px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none\">")
                    .append("Xem vé của tôi</a></td>")
                    .append("<td style=\"padding-left:16px;font-size:14px\"><a href=\"")
                    .append(e(base + "/events/" + event.id())).append("\" style=\"color:").append(PRIMARY_DARK)
                    .append(";font-weight:600;text-decoration:none\">Chi tiết sự kiện →</a></td></tr></table>");
        }
        html.append("</td></tr>");

        // Từng vé: QR, số thứ tự, mã vé.
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
        html.append("</td></tr>");

        // Hướng dẫn vào cửa.
        html.append("<tr><td style=\"padding:8px 28px 28px\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(ACCENT_BG).append(";border-radius:10px\"><tr><td style=\"padding:16px 18px;font-size:14px;")
                .append("line-height:1.6;color:").append(TEXT).append("\">")
                .append("<b>Khi đến sự kiện</b><br>")
                .append("• Mở email này hoặc trang <i>Vé của tôi</i>, đưa mã QR cho nhân viên quét.<br>")
                .append("• Mỗi mã chỉ dùng được một lần. Đi cùng bạn bè thì mỗi người một vé.<br>")
                .append("• Không đăng ảnh QR lên mạng xã hội để tránh bị người khác dùng trước.")
                .append("</td></tr></table></td></tr>");

        // Chân email.
        html.append("<tr><td style=\"padding:16px 28px;border-top:1px solid ").append(BORDER)
                .append(";font-size:12px;line-height:1.6;color:").append(MUTED).append("\">")
                .append("Email được gửi tự động từ OpenEvent vì bạn đã mua vé. Vui lòng không trả lời email này.")
                .append("</td></tr>");

        html.append("</table></td></tr></table></body></html>");
        return html.toString();
    }

    private static void row(StringBuilder html, String label, String valueHtml) {
        html.append("<tr><td valign=\"top\" style=\"width:96px;padding:6px 0;color:").append(MUTED).append("\">")
                .append(e(label)).append("</td><td valign=\"top\" style=\"padding:6px 0\">").append(valueHtml)
                .append("</td></tr>");
    }

    private static String day(ZonedDateTime time) {
        return weekday(time.getDayOfWeek()) + ", " + DATE.format(time);
    }

    private static String weekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Thứ hai";
            case TUESDAY -> "Thứ ba";
            case WEDNESDAY -> "Thứ tư";
            case THURSDAY -> "Thứ năm";
            case FRIDAY -> "Thứ sáu";
            case SATURDAY -> "Thứ bảy";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private static String e(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
