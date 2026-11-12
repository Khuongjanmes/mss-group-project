package com.openevent.notification.notifications;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Một thông báo đã gửi cho một người (requirement.md mục 2.3, collection {@code notifications}).
 * <p>
 * BR-11: mỗi người chỉ có một thông báo cho mỗi message. Khóa là cặp (messageId, userId) vì một message
 * {@code TicketsCancelled} báo cho nhiều người; message được thử lại thì người đã nhận không bị gửi lần hai.
 */
@Document("notifications")
@CompoundIndex(name = "message_user", def = "{'messageId': 1, 'userId': 1}", unique = true)
// Hộp thông báo: của một người, mới nhất trước; đếm chưa đọc dùng phần đầu của index.
@CompoundIndex(name = "user_sent", def = "{'userId': 1, 'read': 1, 'sentAt': -1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    private String id;

    private UUID userId;

    private NotificationType type;

    private String title;

    private String content;

    private NotificationChannel channel;

    private boolean read;

    private Instant sentAt;

    private UUID messageId;

    public static Notification sent(UUID messageId, UUID userId, NotificationType type, String title, String content,
            NotificationChannel channel, Instant sentAt) {
        Notification notification = new Notification();
        notification.messageId = messageId;
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.content = content;
        notification.channel = channel;
        notification.sentAt = sentAt;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }
}
