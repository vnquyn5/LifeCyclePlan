# AUTH-01 Security & Privacy Review

**Review date:** 2026-11-02  
**WBS:** 1.1.10  
**Status:** PARTIAL — development retention policy is approved and recorded; authentication log retention automation and deletion verification remain pending. The status migration is tested in isolation but has not been applied to the existing local database. Production policy remains pending review.

## Executive summary

AUTH-01 validates registration and login input, normalizes email, stores BCrypt hashes at cost 12, uses generic credentials errors for unknown email and wrong password, and does not return password hashes. Auth requests are not logged by application code. Error responses omit exception details. Correlation IDs are bounded and replaced with server-generated UUIDs when invalid. Spring Boot's generated in-memory password is disabled.

The backend now applies bounded, in-memory fixed-window rate limits to login and registration. An HTTP 429 response includes `AUTH_RATE_LIMITED`, a generic message, `Retry-After`, and the request correlation ID. This is suitable for the current single-process local/MVP deployment; it is not a distributed production limiter.

The Project Owner approved a development retention decision on 2026-11-02. WBS 1.1.10 remains **PARTIAL** because authentication log retention automation and deletion verification are pending. Account deletion/anonymization is deferred to privacy/account work; production legal/privacy review is pending; token/session retention depends on AUTH-02. The decision does not certify legal compliance or production readiness. No migration was run against the existing local database during this review.

## Security findings

| ID | Severity | Finding | Action/status |
|---|---|---|---|
| SEC-AUTH-01 | High | Login/register had no server-side abuse control. | Fixed-window limits added; 429/Retry-After and tests added. Single-process limitation remains. |
| SEC-AUTH-02 | Medium | V1 allowed `INACTIVE`, but the Java enum writes `DISABLED`; disabling an account could violate the database check. | V2 normalizes legacy `INACTIVE` to `DISABLED` and replaces the check. Tested in an isolated PostgreSQL container; not applied to existing databases. |
| SEC-AUTH-03 | Medium | A disabled account with a wrong password previously returned a distinct 403, revealing account state. | Password verification now precedes account-state checks. Wrong credentials get the generic 401; correct credentials for a disabled account remain 403. |
| SEC-AUTH-04 | Low | Client supplied correlation IDs could be unbounded or contain unsafe characters. | Existing change limits IDs to 64 safe ASCII characters and generates a UUID for invalid values. |
| SEC-AUTH-05 | Low | Spring Boot generated and logged an implicit in-memory password despite there being no AUTH-02 session implementation. | Existing change supplies an empty `UserDetailsService`; no default account is created. |
| SEC-AUTH-06 | Medium | Development retention policy and enforced log retention are separate controls. | Development policy is approved in the decision record; 90-day authentication-log retention and deletion verification are pending. Account deletion/anonymization is deferred; production review is pending. |

## Rate-limiting policy and implementation

| Endpoint | Limit | Window |
|---|---:|---:|
| `POST /api/v1/auth/login` | 5 requests | 60 seconds |
| `POST /api/v1/auth/register` | 3 requests | 10 minutes |

The limits are configurable under `app.security.auth-rate-limit` in `application.yml`. Each bucket is keyed by endpoint and `HttpServletRequest.getRemoteAddr()`. The limiter does not read request bodies, email addresses, passwords, tokens, or `X-Forwarded-For`; it therefore does not create account-existence-dependent limits. It never logs bucket keys.

The fixed-window map is synchronized, expires old entries during periodic request-time cleanup (30-second interval), and caps storage at 10,000 endpoint/source buckets. If the cap is full, new source buckets are rejected with 429 until capacity becomes available. Existing active buckets retain their own configured windows.

The error body uses the common `ApiErrorResponse` shape. `Retry-After` is a positive integer number of seconds. The response is generic and uses `Cache-Control: no-store`.

**Limitations:** State is process-local and resets on restart. Multiple backend instances do not share counters. A reverse proxy that does not preserve the actual peer address may cause all clients to share one bucket; forwarded headers are intentionally not trusted until a trusted proxy is configured. For multi-instance production, use a shared atomic limiter or a trusted edge gateway and revisit the policy.

## Database consistency

The read-only status count on the existing local `lifecycle_planner` database found only `ACTIVE` rows. It was not modified. V1's check constraint allowed `ACTIVE`, `INACTIVE`, and `DELETED`; the Java enum and entity use `ACTIVE`, `DISABLED`, and `DELETED`.

`V2__align_user_status_values.sql` drops the old status check, changes legacy `INACTIVE` values to `DISABLED` without deleting rows, and adds the matching `ACTIVE`/`DISABLED`/`DELETED` check. A temporary PostgreSQL 16 container with no volume or published port was used to run V1, insert synthetic `ACTIVE` and `INACTIVE` rows, and run V2. The test confirmed that the old row remained and became `DISABLED`, a new `DISABLED` row was accepted, and `INACTIVE` was rejected. The temporary container was stopped and auto-removed.

V2 remains unapplied to the existing local database. Deploying it requires the normal migration/restart process; no destructive SQL or production database was used.

## Secrets, PII, logs, and error handling

- Passwords are encoded with BCrypt cost 12. Login responses contain user information and a null token object until AUTH-02; password/hash fields are absent from response DTOs.
- Root `.env` and `backend/application-local-secrets.properties` are ignored by Git. Tracked environment examples contain variable names/placeholders; no raw credential was found in tracked source during this review. Never place JWT secrets in `EXPO_PUBLIC_*` variables.
- No application logger or request-body logging filter logs auth request bodies. The global exception handler returns a generic 500 message without stack traces or database details. Security handlers return generic 401/403 bodies.
- Correlation IDs are the only request-tracing value intentionally propagated. Invalid client IDs are replaced. Rate-limit code does not log source addresses or identifiers.
- The current account model stores UUID, email, normalized email, password hash, status, onboarding status, created/updated/last-login timestamps, and soft-delete timestamp. `email` and `email_normalized` currently hold the same normalized value, so they are duplicate PII fields; schema consolidation is not included in this task.
- Rate-limit source addresses exist only in bounded process memory for their configured windows. They are not persisted.
- `mobile/.env.example` has a pre-existing uncommitted LAN endpoint change in this working tree. It is not a credential; this review left that user change untouched. Replace it with a placeholder before sharing the example outside the local environment.

## Data inventory and retention decision

The development decision is recorded in [AUTH-01 Retention Decision Record](auth-01-retention-decision.md). It approves keeping account email, password hash, and status while the account is active unless deletion is requested or another requirement applies. It prohibits logging plaintext passwords, confirmation passwords, authentication tokens, and secrets. Authentication security logs have a maximum 90-day development target, with correlation IDs retained no longer than their containing logs. These controls are not all implemented: log retention automation and deletion verification are pending.

| Data | Purpose/location | Classification | Proposed lifecycle | Owner/dependency |
|---|---|---|---|---|
| User UUID | Relational key in PostgreSQL | Linkable identifier | Keep while account is active; delete or irreversibly anonymize during account-deletion workflow, subject to statutory retention. | Product/privacy owner; AUTH-06 |
| Email and normalized email | Account login, uniqueness, and display in PostgreSQL | PII; two columns currently carry the same normalized value | Keep while account is active; remove/anonymize as part of approved account deletion, subject to statutory retention. | Product/privacy owner; AUTH-06 |
| Password hash | Credential verification in PostgreSQL | Sensitive authentication data | Keep only while account is active; remove with the account. Never copy to logs or analytics. | Security owner; AUTH-06 |
| Status, onboarding status, timestamps | Account lifecycle and auth checks in PostgreSQL | Account metadata | Keep while needed for account operation; delete/anonymize with account under the approved deletion schedule. | Product/privacy owner; AUTH-06 |
| `last_login_at` | Security/account activity in PostgreSQL | Personal activity metadata | No duration was set by this decision; resolve in the applicable privacy/account policy. | Privacy/account work |
| Rate-limit peer address bucket | Abuse protection in process memory | Network identifier/personal data | Expire with its 60-second or 10-minute window; cleanup runs periodically; process restart clears state. | Backend owner; implemented in this task |
| Authentication security logs and correlation IDs | Security monitoring and request troubleshooting | Request metadata; logs may include other application messages | Approved development target: at most 90 days; correlation ID follows the containing log's lifetime. Automation and deletion verification are pending. Production period is pending legal/privacy review. | Project Owner approved policy; operations implementation pending |

No account deletion, data export, or log-retention mechanism was added. Docker/application currently use console logging; the repository does not configure or verify 90-day log deletion. Implement and verify it before marking the control implemented. Account deletion/anonymization and lawful retention exceptions belong to later privacy/account-lifecycle work.

## Dependencies and residual risks

- **AUTH-02:** Access/refresh token issuance, session storage, rotation/revocation, and authenticated ownership tests remain out of scope. No token or authenticated user was fabricated.
- **AUTH-06/privacy work:** Account deletion, data export, and approved retention enforcement remain future work.
- **Rate limiter:** Appropriate for one backend process only; not shared across replicas or restarts.
- **Database migration:** V2 is tested but must still be applied to each intended database through a controlled Flyway deployment.
- **Retention:** Development policy is approved; log retention automation/deletion verification is pending. Production legal/privacy review is pending.
