package com.openevent.notification.notifications;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.web.util.HtmlUtils;

import com.openevent.notification.client.EventClient.EventSummary;

/**
 * Khung chung của email OpenEvent. Bố cục bằng bảng và style inline vì Gmail, Outlook bỏ CSS ngoài và flexbox;
 * màu lấy từ token của frontend ({@code --primary} cam, {@code --foreground}). Chữ trắng trên nền cam luôn đậm và
 * từ 14px. Các hàm nhận HTML đã escape trừ khi tên tham số là {@code text}.
 */
final class EmailLayout {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    static final String PRIMARY = "#E8702A";
    static final String PRIMARY_DARK = "#B4501A";
    static final String TEXT = "#1F1D33";
    static final String MUTED = "#6B6B80";
    static final String BORDER = "#E5E7EB";
    static final String ACCENT_BG = "#FFF4EC";
    static final String DANGER = "#B42318";
    static final String DANGER_BG = "#FEF3F2";

    private static final String PAGE_BG = "#F4F5F7";
    private static final String FONT = "'Be Vietnam Pro',Arial,Helvetica,sans-serif";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private EmailLayout() {
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

    static String e(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text, "UTF-8");
    }

    /** Mở email: tiêu đề tab, dòng xem trước trong hộp thư, dải tiêu đề màu cam. */
    static StringBuilder begin(String documentTitleText, String preheaderText, String bannerText) {
        StringBuilder html = new StringBuilder(8_000);
        html.append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(e(documentTitleText)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG).append("\">")
                // Dòng xem trước trong hộp thư, không hiện trong nội dung.
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0\">").append(e(preheaderText))
                .append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(PAGE_BG).append("\"><tr><td align=\"center\" style=\"padding:24px 12px\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"width:600px;max-width:100%;background:#FFFFFF;border:1px solid ").append(BORDER)
                .append(";border-radius:12px;overflow:hidden;font-family:").append(FONT).append(";color:").append(TEXT)
                .append("\">")
                .append("<tr><td style=\"background:").append(PRIMARY).append(";padding:20px 28px\">")
                .append("<div style=\"font-size:20px;font-weight:700;color:#FFFFFF;letter-spacing:.2px\">OpenEvent</div>")
                .append("<div style=\"font-size:14px;font-weight:600;color:#FFFFFF;margin-top:4px\">").append(e(bannerText))
                .append("</div></td></tr>");
        return html;
    }

    /** Bắt đầu phần thân: lời chào, một câu dẫn, tên sự kiện. Đóng bằng {@link #endSection}. */
    static void intro(StringBuilder html, String nameText, String leadText, String eventTitleText, String badgeHtml) {
        html.append("<tr><td style=\"padding:28px 28px 8px\">")
                .append("<p style=\"margin:0 0 8px;font-size:15px\">Xin chào <b>").append(e(nameText)).append("</b>,</p>")
                .append("<p style=\"margin:0 0 20px;font-size:15px;line-height:1.6;color:").append(MUTED).append("\">")
                .append(e(leadText)).append("</p>")
                .append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:").append(TEXT).append("\">")
                .append(e(eventTitleText)).append(badgeHtml).append("</h1>");
    }

    /** Bảng thời gian, địa điểm của sự kiện, kèm các dòng thêm (nhãn, HTML) nếu có. */
    static void eventDetails(StringBuilder html, EventSummary event, String... extraLabelAndHtml) {
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"font-size:14px;line-height:1.5\">");
        row(html, "Thời gian", e(when(event.startTime(), event.endTime())));
        row(html, "Địa điểm",
                "<b>" + e(event.venueName()) + "</b><br>" + e(event.venueAddress() + ", " + event.venueCity()));
        for (int i = 0; i + 1 < extraLabelAndHtml.length; i += 2) {
            row(html, extraLabelAndHtml[i], extraLabelAndHtml[i + 1]);
        }
        html.append("</table>");
    }

    /** Nút chính và một link phụ, dẫn về SPA. Không có địa chỉ SPA thì không vẽ gì. */
    static void buttons(StringBuilder html, String frontendUrl, String primaryPath, String primaryText,
            String secondaryPath, String secondaryText) {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return;
        }
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 4px\"><tr>")
                .append("<td style=\"background:").append(PRIMARY).append(";border-radius:8px\">")
                .append("<a href=\"").append(e(base + primaryPath)).append("\" style=\"display:inline-block;")
                .append("padding:12px 22px;font-size:15px;font-weight:700;color:#FFFFFF;text-decoration:none\">")
                .append(e(primaryText)).append("</a></td>")
                .append("<td style=\"padding-left:16px;font-size:14px\"><a href=\"").append(e(base + secondaryPath))
                .append("\" style=\"color:").append(PRIMARY_DARK).append(";font-weight:600;text-decoration:none\">")
                .append(e(secondaryText)).append(" →</a></td></tr></table>");
    }

    static void endSection(StringBuilder html) {
        html.append("</td></tr>");
    }

    /** Khung chú ý: tiêu đề đậm và các dòng gạch đầu dòng (chữ thường, sẽ được escape). */
    static void notice(StringBuilder html, String background, String titleText, String... linesText) {
        html.append("<tr><td style=\"padding:8px 28px 28px\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
                .append(background).append(";border-radius:10px\"><tr><td style=\"padding:16px 18px;font-size:14px;")
                .append("line-height:1.6;color:").append(TEXT).append("\"><b>").append(e(titleText)).append("</b>");
        for (String line : linesText) {
            html.append("<br>• ").append(e(line));
        }
        html.append("</td></tr></table></td></tr>");
    }

    /** Chân email và đóng các thẻ đã mở ở {@link #begin}. */
    static String end(StringBuilder html, String footerText) {
        html.append("<tr><td style=\"padding:16px 28px;border-top:1px solid ").append(BORDER)
                .append(";font-size:12px;line-height:1.6;color:").append(MUTED).append("\">").append(e(footerText))
                .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    private static void row(StringBuilder html, String labelText, String valueHtml) {
        html.append("<tr><td valign=\"top\" style=\"width:96px;padding:6px 0;color:").append(MUTED).append("\">")
                .append(e(labelText)).append("</td><td valign=\"top\" style=\"padding:6px 0\">").append(valueHtml)
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
}
