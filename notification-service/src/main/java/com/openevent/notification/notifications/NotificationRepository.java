package com.openevent.notification.notifications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);

    /** Những người đã được báo cho message này (message được thử lại sau khi gửi được một phần). */
    List<Notification> findByMessageId(UUID messageId);

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    /** Lọc theo cả người nhận: thông báo của người khác coi như không có. */
    Optional<Notification> findByIdAndUserId(String id, UUID userId);
}
