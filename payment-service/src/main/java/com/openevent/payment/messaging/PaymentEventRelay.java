package com.openevent.payment.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.PaymentFailed;
import com.openevent.common.messaging.events.PaymentSucceeded;

import lombok.RequiredArgsConstructor;

/**
 * Phát kết quả thanh toán sau khi commit, để Order không nhận kết quả của giao dịch bị rollback.
 * shortcut: broker lỗi ngay sau commit thì message mất (giống Event), cần outbox nếu làm mục dự phòng 3.5.
 */
@Component
@RequiredArgsConstructor
class PaymentEventRelay {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener
    void send(PaymentSucceeded event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PAYMENT_SUCCEEDED, event);
    }

    @TransactionalEventListener
    void send(PaymentFailed event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PAYMENT_FAILED, event);
    }
}
