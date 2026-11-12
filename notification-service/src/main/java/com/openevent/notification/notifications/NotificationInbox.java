package com.openevent.notification.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.openevent.common.web.ApiException;

import lombok.RequiredArgsConstructor;

/** Hộp thông báo trong ứng dụng (#62): xem thông báo của mình, đếm chưa đọc, đánh dấu đã đọc. */
@Service
@RequiredArgsConstructor
public class NotificationInbox {

    /** id thêm vào để thứ tự ổn định khi hai thông báo cùng thời điểm. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"));

    private final NotificationRepository notifications;

    public record NotificationResponse(String id, NotificationType type, String title, String content,
            NotificationChannel channel, boolean read, Instant sentAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getChannel(),
                    n.isRead(), n.getSentAt());
        }
    }

    /** Như {@code PageResponse} của common-lib, thêm số chưa đọc để chuông và trang danh sách khớp nhau. */
    public record InboxPage(List<NotificationResponse> content, int page, int size, long totalElements, int totalPages,
            long unreadCount) {
    }

    public record UnreadCount(long unreadCount) {
    }

    public InboxPage mine(UUID userId, int page, int size) {
        Page<Notification> result = notifications.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST));
        return new InboxPage(result.getContent().stream().map(NotificationResponse::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages(), unread(userId).unreadCount());
    }

    public UnreadCount unread(UUID userId) {
        return new UnreadCount(notifications.countByUserIdAndReadFalse(userId));
    }

    /** Đánh dấu lại thông báo đã đọc không lỗi. Thông báo của người khác trả 404 như không tồn tại. */
    public void markRead(UUID userId, String id) {
        Notification notification = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "Không tìm thấy thông báo"));
        if (!notification.isRead()) {
            notification.markRead();
            notifications.save(notification);
        }
    }
}
