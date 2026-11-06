CREATE TABLE users (
    id               UUID PRIMARY KEY,
    email            VARCHAR(254) NOT NULL UNIQUE,
    password_hash    VARCHAR(100),
    full_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    avatar_url       VARCHAR(500),
    provider         VARCHAR(10)  NOT NULL CHECK (provider IN ('LOCAL', 'GOOGLE')),
    provider_subject VARCHAR(255),
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'LOCKED')),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_provider_credentials CHECK (
        (provider = 'LOCAL' AND password_hash IS NOT NULL)
        OR (provider = 'GOOGLE' AND provider_subject IS NOT NULL))
);

CREATE UNIQUE INDEX users_google_subject_uq ON users (provider_subject) WHERE provider = 'GOOGLE';
CREATE INDEX users_created_at_idx ON users (created_at DESC);

CREATE TABLE roles (
    id   SMALLINT PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

INSERT INTO roles (id, name) VALUES (1, 'ATTENDEE'), (2, 'ORGANIZER'), (3, 'ADMIN');

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);
