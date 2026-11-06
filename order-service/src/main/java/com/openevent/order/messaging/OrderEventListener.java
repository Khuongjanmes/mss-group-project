package com.openevent.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.EventCancelled;

/** Khung nhận sự kiện theo requirement.md mục 3.2 và KB-4, KB-5. */
@Component
@RabbitListener(queues = MessagingConfig.QUEUE)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @RabbitHandler
    public void on(PaymentSucceeded event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận PaymentSucceeded: {}", event.messageId());
    }

    @RabbitHandler
    public void on(PaymentFailed event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận PaymentFailed: {}", event.messageId());
    }

    @RabbitHandler
    public void on(EventCancelled event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận EventCancelled: {}", event.messageId());
    }
}
