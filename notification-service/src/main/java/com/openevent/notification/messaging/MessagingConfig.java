package com.openevent.notification.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openevent.common.messaging.Messaging;

@Configuration
class MessagingConfig {

    static final String QUEUE = "notification-service.events";

    @Bean
    Declarables notificationQueue() {
        return Messaging.queueWithBindings(QUEUE, Messaging.TICKET_ISSUED, Messaging.TICKETS_CANCELLED,
                Messaging.PASSWORD_RESET_REQUESTED);
    }
}
