package com.openevent.event.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.openevent.common.messaging.Messaging;
import com.openevent.common.messaging.events.EventCancelled;

import lombok.RequiredArgsConstructor;

/**
 * Chỉ gửi EventCancelled sau khi trạng thái CANCELLED đã lưu, để không báo hủy một sự kiện bị rollback.
 * shortcut: broker lỗi đúng lúc này thì mất message; làm outbox (mục dự phòng 3.5) nếu cần chắc chắn.
 */
@Component
@RequiredArgsConstructor
class EventCancelledRelay {

    private final RabbitTemplate rabbit;

    @TransactionalEventListener
    void send(EventCancelled event) {
        rabbit.convertAndSend(Messaging.EXCHANGE, Messaging.EVENT_CANCELLED, event);
    }
}
