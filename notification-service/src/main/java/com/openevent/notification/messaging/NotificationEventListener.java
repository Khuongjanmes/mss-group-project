package com.openevent.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;
import com.openevent.notification.notifications.TicketIssuedNotifier;

import lombok.RequiredArgsConstructor;

/** Nhận sự kiện theo requirement.md mục 3.2 và KB-4, KB-5. Message trùng bị bỏ qua theo messageId (BR-11). */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = MessagingConfig.QUEUE)
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final TicketIssuedNotifier ticketIssued;

    @RabbitHandler
    public void on(TicketIssued event) {
        log.info("Nhận TicketIssued: {}", event.messageId());
        ticketIssued.handle(event);
    }

    @RabbitHandler
    public void on(TicketsCancelled event) {
        // TODO(#61): gửi email báo sự kiện bị hủy; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận TicketsCancelled: {}", event.messageId());
    }
}
