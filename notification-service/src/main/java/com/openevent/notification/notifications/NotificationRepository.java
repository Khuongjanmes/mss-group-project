package com.openevent.notification.notifications;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
}
