package com.openevent.payment.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.OrderRefundRequested;

/** Khung nhận sự kiện theo requirement.md mục 3.2 và KB-4, KB-5. */
@Component
@RabbitListener(queues = MessagingConfig.QUEUE)
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    @RabbitHandler
    public void on(OrderRefundRequested event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận OrderRefundRequested: {}", event.messageId());
    }
}
