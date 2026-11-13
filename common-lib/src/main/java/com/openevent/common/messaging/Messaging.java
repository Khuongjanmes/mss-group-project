package com.openevent.common.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/** Tên exchange và routing key của RabbitMQ (requirement.md, KB-4 và KB-5). */
public final class Messaging {

    public static final String EXCHANGE = "openevent.events";
    public static final String DEAD_LETTER_EXCHANGE = "openevent.events.dlx";

    public static final String EVENT_CANCELLED = "event.cancelled";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_REFUND_REQUESTED = "order.refund-requested";
    public static final String TICKET_ISSUED = "ticket.issued";
    public static final String TICKETS_CANCELLED = "tickets.cancelled";
    public static final String PASSWORD_RESET_REQUESTED = "identity.password-reset-requested";

    private Messaging() {
    }

    /**
     * Khai báo queue của một service, các binding và dead-letter queue {@code <queue>.dlq}. Message xử lý lỗi
     * sau khi hết retry sẽ vào dead-letter queue thay vì lặp vô hạn.
     */
    public static Declarables queueWithBindings(String queueName, String... routingKeys) {
        Queue queue = QueueBuilder.durable(queueName)
                .withArguments(Map.of("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", queueName))
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(queueName + ".dlq").build();
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        TopicExchange exchange = new TopicExchange(EXCHANGE);

        List<Declarable> declarables = new ArrayList<>(List.of(queue, deadLetterQueue, deadLetterExchange, exchange,
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(queueName)));
        for (String routingKey : routingKeys) {
            declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        }
        return new Declarables(declarables);
    }
}
