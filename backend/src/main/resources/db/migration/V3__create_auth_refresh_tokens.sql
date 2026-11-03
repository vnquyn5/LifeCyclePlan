CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_auth_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_refresh_tokens_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_auth_refresh_tokens_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_auth_refresh_tokens_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE UNIQUE INDEX ux_auth_refresh_tokens_token_hash
    ON auth_refresh_tokens(token_hash);

CREATE INDEX ix_auth_refresh_tokens_user_expiry
    ON auth_refresh_tokens(user_id, expires_at);

CREATE INDEX ix_auth_refresh_tokens_expiry
    ON auth_refresh_tokens(expires_at);
