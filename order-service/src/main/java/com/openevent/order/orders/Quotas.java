package com.openevent.order.orders;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * BR-03: mỗi tài khoản tối đa {@value #MAX_PER_EVENT} vé cho mỗi sự kiện. Cộng và trừ đều là một câu lệnh có điều
 * kiện, nên hai request cùng lúc không vượt giới hạn (Postgres khóa dòng khi {@code ON CONFLICT DO UPDATE}).
 */
@Component
@RequiredArgsConstructor
public class Quotas {

    public static final int MAX_PER_EVENT = 5;

    private final JdbcTemplate jdbc;

    /** Giữ thêm {@code quantity} vé; trả false nếu vượt giới hạn. Phải chạy trong transaction tạo đơn. */
    public boolean take(UUID userId, UUID eventId, int quantity) {
        if (quantity < 1 || quantity > MAX_PER_EVENT) {
            return false;
        }
        return jdbc.update("""
                INSERT INTO user_event_quotas (user_id, event_id, quantity) VALUES (?, ?, ?)
                ON CONFLICT (user_id, event_id) DO UPDATE
                SET quantity = user_event_quotas.quantity + EXCLUDED.quantity
                WHERE user_event_quotas.quantity + EXCLUDED.quantity <= ?""",
                userId, eventId, quantity, MAX_PER_EVENT) == 1;
    }

    /** Trả lại {@code quantity} vé khi đơn bị hủy hoặc hết hạn. */
    public void giveBack(UUID userId, UUID eventId, int quantity) {
        jdbc.update("""
                UPDATE user_event_quotas SET quantity = quantity - ?
                WHERE user_id = ? AND event_id = ? AND quantity >= ?""",
                quantity, userId, eventId, quantity);
    }
}
