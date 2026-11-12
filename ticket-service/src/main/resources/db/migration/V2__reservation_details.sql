-- Job nhả vé và lệnh "giữ vé của đơn" đọc theo order_id.
CREATE INDEX reservations_order_idx ON reservations (order_id);

-- Đơn giá lúc giữ vé: Order tính tổng tiền theo giá này, gọi lại cùng orderId vẫn nhận đúng giá cũ (KB-1).
ALTER TABLE reservations ADD COLUMN unit_price NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (unit_price >= 0);
ALTER TABLE reservations ALTER COLUMN unit_price DROP DEFAULT;
