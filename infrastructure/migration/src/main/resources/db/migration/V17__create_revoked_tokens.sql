CREATE TABLE IF NOT EXISTS revoked_tokens
(
    jti        UUID        NOT NULL PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
