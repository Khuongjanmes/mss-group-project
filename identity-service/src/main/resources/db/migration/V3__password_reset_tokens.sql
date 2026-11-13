-- Quên mật khẩu: chỉ lưu bản băm SHA-256 của token, dùng một lần, có hạn.
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);
