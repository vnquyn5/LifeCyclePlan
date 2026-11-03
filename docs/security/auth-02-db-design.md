# AUTH-02 Refresh Session Database Design

**WBS:** 1.2.3  
**Status:** Schema design and isolated PostgreSQL migration/JPA validation complete.  
**Migrations:** V3 `auth_refresh_tokens`, V4 `auth_refresh_sessions` and token lineage.

## Data model

```text
users 1 ─── * auth_refresh_sessions 1 ─── * auth_refresh_tokens
                                      token.replaced_by_token_id ──> token.id
```

| Table | Fields and purpose |
|---|---|
| `auth_refresh_sessions` | `id` is the stable family/session UUID; `user_id` owns the family; `created_at` records creation; `revoked_at` and `revoked_reason` record family revocation. Reasons are constrained to `REFRESH_REUSE` and `USER_UNAVAILABLE`. |
| `auth_refresh_tokens` | One row per refresh token generation: `id`, `user_id`, `session_id`, SHA-256 `token_hash`, `created_at`, `expires_at`, `revoked_at`, and nullable `replaced_by_token_id`. Rows remain available to detect reuse after rotation. |

Raw refresh credentials are never stored. The application hashes the opaque CSPRNG token before persistence and looks up rows by the lowercase 64-character SHA-256 digest. Access JWTs are stateless and are not stored in these tables.

## Constraints and indexes

- Primary keys: `auth_refresh_sessions.id`, `auth_refresh_tokens.id`.
- User foreign keys cascade on account deletion. A composite `(session_id, user_id)` foreign key to `(session.id, session.user_id)` prevents a token from belonging to another user's family.
- Unique token-hash index prevents duplicate refresh credentials: `ux_auth_refresh_tokens_token_hash`.
- Check constraints enforce a lowercase 64-hex hash, `expires_at > created_at`, and `revoked_at >= created_at` for both token and session records.
- Session revocation reason is constrained to known values. A future reason requires a deliberate schema update.
- Replacement FK preserves lineage. A partial unique index on `replaced_by_token_id` prevents two prior tokens from pointing to one replacement; null values remain allowed for active/unrotated tokens.
- `ix_auth_refresh_tokens_user_expiry` and `ix_auth_refresh_tokens_expiry` support per-user lookup and expiry cleanup. `ix_auth_refresh_tokens_session` supports family revocation and audit queries.

## Rotation, reuse and concurrency guidance

The refresh operation must be one database transaction. Resolve the presented token hash, obtain its `session_id`, then acquire `SELECT ... FOR UPDATE` on the session row before deciding whether the family is active. The repository exposes `findByIdForUpdate`; the token repository also exposes a token-row lock method. Lock ordering must remain consistent across refresh and revoke operations to avoid deadlocks.

Under the family lock, re-read/check token and family state. If the token is active and unexpired, create the successor, mark the old row revoked and set `replaced_by_token_id`, then commit all changes together. If a rotated token is presented again, revoke the family with `REFRESH_REUSE` and commit the revocation before returning the generic invalid-refresh response. Concurrent callers serialize on the same session row: only the transaction that consumes an active token may rotate it; the later transaction observes the consumed token and triggers reuse policy. Unique constraints are final integrity guards, not a substitute for this transaction/lock protocol.

The current repository provides pessimistic lock queries for token and family rows. The concrete service transaction boundary and concurrency tests belong to the implementation work; this document defines the required usage. A refresh 401 must not reveal whether a token was unknown, expired, revoked, or reused.

## Migration safety and data preservation

- V3 creates token storage with a unique hash index, user FK, expiry/revocation checks and expiry lookup indexes.
- V4 adds `session_id`, backfills each existing token into a new session with `session_id = token.id`, and copies the existing user's and creation timestamp into that session. Existing token IDs, hashes, expiry, and revocation values remain untouched.
- V4 then makes `session_id` required and adds same-user session FK plus replacement lineage FK/index.
- Do not edit an already applied migration. Changes to this model after V4 require a new forward-only migration.
- Apply migrations through Flyway against the target environment after backup/change review. Do not manually reset a database or alter Flyway history. The local development database is not a migration test target for this design task.

## Retention and cleanup

Keep consumed token rows and family revocation state long enough to enforce reuse detection through the refresh token's maximum 7-day validity window and the chosen operational investigation window. No cleanup job or exact post-expiry retention interval is implemented by these migrations; define and test that policy before adding deletion. Deleting a user cascades session and token rows. Account deletion workflow remains a separate privacy task.

## Verification evidence and remaining work

- Entity mappings use `TokenHash` plus `TokenHashConverter`; entity constructors reject invalid temporal ordering and prevent re-consuming a rotated token.
- Repositories provide hash lookup, token/family pessimistic locks, and family token queries.
- V1–V4 ran successfully through Flyway on an empty isolated PostgreSQL 16 database; Flyway history recorded all four versions as successful and Spring Boot completed startup with Hibernate `ddl-auto=validate`.
- A separate isolated migration sequence applied V1–V3, inserted one synthetic pre-V4 token row, then applied V4. Assertions confirmed the legacy token ID/hash/expiry stayed unchanged and its session row was backfilled using `session_id = token.id` and the original owner/creation timestamp.
- Catalog assertions confirmed the expected indexes and check/foreign-key/unique constraints exist. Negative inserts/updates confirmed enforcement of token hash format, token expiry, token/session revocation time, supported session revocation reason, unique token hash, unique replacement, user FK, and same-user session FK. No project local database or persistent Docker volume was used.
- No production/local database write is required for this deliverable; test only against an isolated disposable database.
