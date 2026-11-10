package com.openevent.ticket.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** BR-11: ghi messageId trong cùng transaction với nghiệp vụ; trả false nếu message đã xử lý. */
@Component
@RequiredArgsConstructor
public class ProcessedMessages {

    private final JdbcTemplate jdbc;

    public boolean markNew(UUID messageId) {
        return jdbc.update("insert into processed_messages (message_id, processed_at) values (?, now()) "
                + "on conflict do nothing", messageId) == 1;
    }
}
