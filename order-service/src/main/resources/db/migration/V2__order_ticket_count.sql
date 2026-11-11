-- BR-03: số vé đã trừ quota khi mở đơn. Đơn hủy hoặc hết hạn trả lại đúng số này, kể cả khi service dừng giữa lúc
-- mở đơn và lúc Ticket giữ vé xong (đơn chưa có dòng order_items nào).
ALTER TABLE orders ADD COLUMN ticket_count INTEGER NOT NULL DEFAULT 0 CHECK (ticket_count BETWEEN 0 AND 5);
