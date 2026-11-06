package com.openevent.order.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openevent.common.messaging.Messaging;

@Configuration
class MessagingConfig {

    static final String QUEUE = "order-service.events";

    @Bean
    Declarables orderQueue() {
        return Messaging.queueWithBindings(QUEUE, Messaging.PAYMENT_SUCCEEDED, Messaging.PAYMENT_FAILED, Messaging.EVENT_CANCELLED);
    }
}
