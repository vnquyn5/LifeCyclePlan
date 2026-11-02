CREATE TABLE users(
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    onboarding_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    last_login_at TIMESTAMPTZ,  
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delete_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    CONSTRAINT ck_users_onboarding_status CHECK (onboarding_status 
    IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE UNIQUE INDEX ux_users_email_normalized ON users(email_normalized);