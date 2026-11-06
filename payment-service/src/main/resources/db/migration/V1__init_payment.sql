CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    order_id        UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    method          VARCHAR(10)    NOT NULL CHECK (method IN ('VNPAY', 'MOCK')),
    status          VARCHAR(15)    NOT NULL CHECK (status IN
                    ('PENDING', 'SUCCESS', 'FAILED', 'REFUND_PENDING', 'REFUNDED')),
    transaction_ref VARCHAR(64)    NOT NULL UNIQUE, -- BR-11
    paid_at         TIMESTAMPTZ
);

CREATE INDEX payments_order_idx ON payments (order_id);
CREATE INDEX payments_status_idx ON payments (status);

CREATE TABLE payment_logs (
    id           UUID PRIMARY KEY,
    payment_id   UUID        NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    raw_callback TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
