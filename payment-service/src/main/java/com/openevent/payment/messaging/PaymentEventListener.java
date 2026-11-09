package com.openevent.payment.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.OrderRefundRequested;
import com.openevent.payment.payments.PaymentService;

import lombok.RequiredArgsConstructor;

/** Order yêu cầu hoàn tiền khi hủy sự kiện hoặc thanh toán đến sau khi đơn hết hạn (KB-4, KB-5). */
@Component
@RabbitListener(queues = MessagingConfig.QUEUE)
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;

    @RabbitHandler
    public void on(OrderRefundRequested event) {
        paymentService.requestRefund(event);
    }
}
