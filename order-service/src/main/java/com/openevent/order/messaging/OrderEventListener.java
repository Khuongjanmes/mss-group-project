package com.openevent.order.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;
import com.openevent.order.orders.OrderStore;

import lombok.RequiredArgsConstructor;

/** Nhận sự kiện theo requirement.md mục 3.2 và KB-4, KB-5. Message trùng bị bỏ qua theo messageId (BR-11). */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = MessagingConfig.QUEUE)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderStore orders;

    @RabbitHandler
    public void on(PaymentSucceeded event) {
        orders.paymentSucceeded(event);
    }

    @RabbitHandler
    public void on(PaymentFailed event) {
        orders.paymentFailed(event);
    }

    @RabbitHandler
    public void on(EventCancelled event) {
        // TODO(#37): hủy đơn của sự kiện; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận EventCancelled: {}", event.messageId());
    }
}
