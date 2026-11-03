CREATE TABLE auth_refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(32),
    CONSTRAINT fk_auth_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ux_auth_refresh_sessions_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_auth_refresh_sessions_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    CONSTRAINT ck_auth_refresh_sessions_revoked_reason
        CHECK (revoked_reason IS NULL OR revoked_reason IN ('REFRESH_REUSE', 'USER_UNAVAILABLE'))
);

ALTER TABLE auth_refresh_tokens
    ADD COLUMN session_id UUID;

UPDATE auth_refresh_tokens
SET session_id = id;

INSERT INTO auth_refresh_sessions (id, user_id, created_at)
SELECT session_id, user_id, created_at
FROM auth_refresh_tokens;

ALTER TABLE auth_refresh_tokens
    ALTER COLUMN session_id SET NOT NULL,
    ADD COLUMN replaced_by_token_id UUID,
    ADD CONSTRAINT fk_auth_refresh_tokens_session_user
        FOREIGN KEY (session_id, user_id)
        REFERENCES auth_refresh_sessions(id, user_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT fk_auth_refresh_tokens_replacement
        FOREIGN KEY (replaced_by_token_id)
        REFERENCES auth_refresh_tokens(id);

CREATE INDEX ix_auth_refresh_tokens_session
    ON auth_refresh_tokens(session_id);

CREATE UNIQUE INDEX ux_auth_refresh_tokens_replaced_by
    ON auth_refresh_tokens(replaced_by_token_id)
    WHERE replaced_by_token_id IS NOT NULL;
