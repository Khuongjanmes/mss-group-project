package com.openevent.identity.auth;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.PasswordResetRequested;

import lombok.RequiredArgsConstructor;

/** Gửi sau khi lưu token thành công, để email không chứa link của token đã bị rollback. */
@Component
@RequiredArgsConstructor
class PasswordResetRelay {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener
    void send(PasswordResetRequested event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.PASSWORD_RESET_REQUESTED, event);
    }
}
