package com.openevent.payment.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.openevent.common.messaging.Messaging;

@Configuration
class MessagingConfig {

    static final String QUEUE = "payment-service.events";

    @Bean
    Declarables paymentQueue() {
        return Messaging.queueWithBindings(QUEUE, Messaging.ORDER_REFUND_REQUESTED);
    }
}
