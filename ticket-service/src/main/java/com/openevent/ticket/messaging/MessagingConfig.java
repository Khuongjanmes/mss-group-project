package com.openevent.ticket.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openevent.common.messaging.Messaging;

@Configuration
class MessagingConfig {

    static final String QUEUE = "ticket-service.events";

    @Bean
    Declarables ticketQueue() {
        return Messaging.queueWithBindings(QUEUE, Messaging.ORDER_PAID, Messaging.ORDER_CANCELLED, Messaging.EVENT_CANCELLED);
    }
}
