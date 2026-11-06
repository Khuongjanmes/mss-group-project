CREATE TABLE orders (
    id           UUID PRIMARY KEY,
    user_id      UUID           NOT NULL,
    event_id     UUID           NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    status       VARCHAR(10)    NOT NULL CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED')),
    expires_at   TIMESTAMPTZ    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL
);

CREATE INDEX orders_user_idx ON orders (user_id, created_at DESC);
CREATE INDEX orders_event_idx ON orders (event_id);
CREATE INDEX orders_expiry_idx ON orders (status, expires_at);

CREATE TABLE order_items (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    ticket_type_id UUID           NOT NULL,
    quantity       INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price     NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT order_items_unique_type UNIQUE (order_id, ticket_type_id)
);

-- BR-03: tăng quantity bằng một câu lệnh có điều kiện để hai request đồng thời không vượt 5 vé.
CREATE TABLE user_event_quotas (
    user_id  UUID    NOT NULL,
    event_id UUID    NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity BETWEEN 0 AND 5),
    PRIMARY KEY (user_id, event_id)
);

CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
