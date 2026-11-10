-- Thời điểm tạo để Admin xem giao dịch mới nhất trước; lý do hoàn tiền từ OrderRefundRequested (BR-10, BR-13).
ALTER TABLE payments
    ADD COLUMN created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN refund_reason VARCHAR(500);

CREATE INDEX payments_created_at_idx ON payments (created_at DESC);
