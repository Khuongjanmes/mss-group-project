package com.openevent.order.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.OrderCancelled;

import lombok.RequiredArgsConstructor;

/**
 * Phát message sau khi commit, để Ticket không nhả vé của một đơn mà việc hủy bị rollback.
 * shortcut: broker lỗi ngay sau commit thì message mất (giống Event, Payment, Ticket); job của Ticket vẫn nhả vé
 * quá hạn, cần outbox nếu làm mục dự phòng 3.5.
 */
@Component
@RequiredArgsConstructor
class OrderEventRelay {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener
    void send(OrderCancelled event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.ORDER_CANCELLED, event);
    }
}
