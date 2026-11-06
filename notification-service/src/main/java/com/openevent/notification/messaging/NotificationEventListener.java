package com.openevent.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.openevent.common.messaging.events.TicketIssued;
import com.openevent.common.messaging.events.TicketsCancelled;

/** Khung nhận sự kiện theo requirement.md mục 3.2 và KB-4, KB-5. */
@Component
@RabbitListener(queues = MessagingConfig.QUEUE)
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @RabbitHandler
    public void on(TicketIssued event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận TicketIssued: {}", event.messageId());
    }

    @RabbitHandler
    public void on(TicketsCancelled event) {
        // TODO: xử lý nghiệp vụ; bỏ qua nếu event.messageId() đã xử lý (BR-11).
        log.info("Nhận TicketsCancelled: {}", event.messageId());
    }
}
