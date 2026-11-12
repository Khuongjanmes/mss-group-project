package com.openevent.notification.mail;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Gửi email HTML kèm ảnh nhúng. Chưa đặt {@code MAIL_HOST} (máy cá nhân, server chưa có SMTP) thì không gửi,
 * để thông báo vẫn được lưu thay vì message lỗi mãi rồi vào dead-letter queue.
 */
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    EmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username, @Value("${openevent.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.enabled = StringUtils.hasText(host);
        if (StringUtils.hasText(from)) {
            this.from = from;
        } else if (StringUtils.hasText(username)) {
            this.from = "OpenEvent <" + username + ">";
        } else {
            this.from = "OpenEvent <no-reply@openevent.local>";
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** @param inlinePngs ảnh PNG theo content-id, HTML tham chiếu bằng {@code <img src="cid:...">} */
    public void send(String to, String subject, String html, Map<String, byte[]> inlinePngs) {
        JavaMailSender sender = mailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            // Ảnh nhúng phải thêm sau phần nội dung.
            helper.setText(html, true);
            for (Map.Entry<String, byte[]> image : inlinePngs.entrySet()) {
                helper.addInline(image.getKey(), new ByteArrayResource(image.getValue()), "image/png");
            }
        } catch (MessagingException e) {
            throw new MailSendException("Không tạo được email", e);
        }
        sender.send(message);
    }
}
