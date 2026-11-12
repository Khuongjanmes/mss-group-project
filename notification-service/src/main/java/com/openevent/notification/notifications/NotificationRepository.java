package com.openevent.notification.notifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);

    /** Những người đã được báo cho message này (message được thử lại sau khi gửi được một phần). */
    List<Notification> findByMessageId(UUID messageId);
}
