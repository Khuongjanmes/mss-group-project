package com.openevent.ticket.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.TicketIssued;

import lombok.RequiredArgsConstructor;

/**
 * Phát sau khi commit, để Notification không gửi email cho vé bị rollback.
 * shortcut: broker lỗi ngay sau commit thì message mất (giống Event, Payment), cần outbox nếu làm mục dự phòng 3.5.
 */
@Component
@RequiredArgsConstructor
class TicketEventRelay {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener
    void send(TicketIssued event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.TICKET_ISSUED, event);
    }

}
