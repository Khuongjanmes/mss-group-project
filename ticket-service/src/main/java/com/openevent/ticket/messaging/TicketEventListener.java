package com.openevent.ticket.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.EventCancelled;
import com.openevent.common.messaging.events.OrderCancelled;
import com.openevent.common.messaging.events.OrderPaid;
import com.openevent.ticket.reservations.ReservationService;
import com.openevent.ticket.tickets.TicketService;

import lombok.RequiredArgsConstructor;

/** KB-4, KB-5. Mỗi handler bỏ qua message trùng theo messageId (BR-11). */
@Component
@RabbitListener(queues = MessagingConfig.QUEUE)
@RequiredArgsConstructor
public class TicketEventListener {

    private final TicketService tickets;
    private final ReservationService reservations;

    @RabbitHandler
    public void on(OrderPaid event) {
        tickets.issue(event);
    }

    @RabbitHandler
    public void on(OrderCancelled event) {
        reservations.release(event);
    }

    @RabbitHandler
    public void on(EventCancelled event) {
        tickets.cancelEvent(event);
    }
}
