-- V1 stored disabled accounts as INACTIVE, while the current domain enum uses DISABLED.
-- Preserve existing accounts and normalize the legacy value before tightening the constraint.
ALTER TABLE users DROP CONSTRAINT ck_users_status;

UPDATE users
SET status = 'DISABLED'
WHERE status = 'INACTIVE';

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED'));
