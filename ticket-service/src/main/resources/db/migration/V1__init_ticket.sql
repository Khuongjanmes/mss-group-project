CREATE TABLE ticket_types (
    id                 UUID PRIMARY KEY,
    event_id           UUID           NOT NULL,
    name               VARCHAR(100)   NOT NULL,
    price              NUMERIC(14, 2) NOT NULL CHECK (price >= 0),
    total_quantity     INTEGER        NOT NULL CHECK (total_quantity >= 0),
    sold_quantity      INTEGER        NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0),
    reserved_quantity  INTEGER        NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    sale_start         TIMESTAMPTZ    NOT NULL,
    sale_end           TIMESTAMPTZ    NOT NULL,
    -- Bản cache từ Event, dùng cho fallback KB-3.
    event_organizer_id UUID           NOT NULL,
    event_start_time   TIMESTAMPTZ    NOT NULL,
    event_end_time     TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ticket_types_not_oversold CHECK (sold_quantity + reserved_quantity <= total_quantity), -- BR-01
    CONSTRAINT ticket_types_sale_order CHECK (sale_end > sale_start)
);

CREATE INDEX ticket_types_event_idx ON ticket_types (event_id);

CREATE TABLE reservations (
    id             UUID PRIMARY KEY,
    order_id       UUID        NOT NULL,
    ticket_type_id UUID        NOT NULL REFERENCES ticket_types (id),
    quantity       INTEGER     NOT NULL CHECK (quantity > 0),
    status         VARCHAR(10) NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED')),
    expires_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT reservations_idempotent UNIQUE (order_id, ticket_type_id) -- retry trong KB-1
);

CREATE INDEX reservations_expiry_idx ON reservations (status, expires_at);

CREATE TABLE tickets (
    id             UUID PRIMARY KEY,
    ticket_type_id UUID         NOT NULL REFERENCES ticket_types (id),
    order_id       UUID         NOT NULL,
    owner_id       UUID         NOT NULL,
    code           VARCHAR(64)  NOT NULL UNIQUE,
    status         VARCHAR(12)  NOT NULL CHECK (status IN ('ISSUED', 'CHECKED_IN', 'CANCELLED')),
    checked_in_at  TIMESTAMPTZ
);

CREATE INDEX tickets_owner_idx ON tickets (owner_id);
CREATE INDEX tickets_order_idx ON tickets (order_id);
CREATE INDEX tickets_type_idx ON tickets (ticket_type_id);

CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
