CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE venues (
    id       UUID PRIMARY KEY,
    name     VARCHAR(200) NOT NULL,
    address  VARCHAR(300) NOT NULL,
    city     VARCHAR(100) NOT NULL,
    capacity INTEGER CHECK (capacity IS NULL OR capacity > 0)
);

CREATE TABLE events (
    id            UUID PRIMARY KEY,
    organizer_id  UUID         NOT NULL,
    category_id   UUID         NOT NULL REFERENCES categories (id),
    venue_id      UUID         NOT NULL REFERENCES venues (id),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    banner_url    VARCHAR(500),
    start_time    TIMESTAMPTZ  NOT NULL,
    end_time      TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN
                  ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    reject_reason VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT events_time_order CHECK (end_time > start_time) -- BR-07
);

CREATE INDEX events_status_start_idx ON events (status, start_time);
CREATE INDEX events_organizer_idx ON events (organizer_id);
