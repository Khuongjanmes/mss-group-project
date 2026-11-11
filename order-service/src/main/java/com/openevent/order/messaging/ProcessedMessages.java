package com.openevent.order.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * BR-11: consumer idempotent theo {@code messageId}. Gọi trong cùng transaction với việc xử lý, để message lỗi được
 * rollback và nhận lại sẽ xử lý lần nữa.
 */
@Component
@RequiredArgsConstructor
public class ProcessedMessages {

    private final JdbcTemplate jdbc;

    /** true nếu message chưa từng xử lý. */
    public boolean markNew(UUID messageId) {
        return jdbc.update("insert into processed_messages (message_id, processed_at) values (?, now()) "
                + "on conflict do nothing", messageId) == 1;
    }
}
