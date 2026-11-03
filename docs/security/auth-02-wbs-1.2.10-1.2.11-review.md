# AUTH-02 WBS 1.2.10–1.2.11 Security and Integration Review

**Review date:** 2026-11-03  
**Source of scope:** `Master_Plan_AI_Personal_Lifestyle_Planner_Chi_Tiet.xlsx`, sheet `Kế hoạch chi tiết AI Planner`  
**Status:** Partial acceptance; see remaining evidence below.

## Master Plan mapping

| WBS | Exact task | Sprint / priority | Feature and requirement | Deliverable | Acceptance criteria | Dependency | Master Plan status |
|---|---|---|---|---|---|---|---|
| 1.2.10 | `[SEC] Security/privacy review cho AUTH-02: ownership, rate limit/secret/PII/log masking/data-retention theo phạm vi feature` | Sprint 1 / P0 | Xác thực & Tài khoản → Phiên đăng nhập / AUTH-02 | SEC deliverable AUTH-02 | `Happy path + validation pass; token/session đúng lifecycle; quyền truy cập resource được enforce server-side; không lộ secret.` | 1.2 | Pending |
| 1.2.11 | `[INT] Tích hợp end-to-end AUTH-02 giữa mobile ↔ backend; kiểm tra contract và failure/retry path` | Sprint 1 / P0 | Xác thực & Tài khoản → Phiên đăng nhập / AUTH-02 | INT deliverable AUTH-02 | `Happy path + validation pass; token/session đúng lifecycle; quyền truy cập resource được enforce server-side; không lộ secret.` | 1.2 | Pending |

The planned dates are 2026-10-16 for WBS 1.2.10 and 2026-10-17 for WBS 1.2.11 (both 0.5 day). Reviewer roles are Security Reviewer and QA, respectively. The workbook has no more specific acceptance criteria or dependencies for these rows.

## Security review — WBS 1.2.10

| Control | Evidence in current implementation | Review result / remaining work |
|---|---|---|
| Session ownership | Refresh token rows link to a user and a session family; repository design and migration constraints enforce same-user ownership. Login creates a per-session family. | Automated service and PostgreSQL integration coverage passes. No resource-specific product API exists in this scope; protected test route verifies authentication, not a complete business-resource authorization matrix. |
| Token lifecycle | RS256 access JWT (900 seconds); opaque 32-byte refresh token, SHA-256 digest at rest, seven-day expiry, rotation and family revocation on reuse. | Covered by unit and PostgreSQL/Testcontainers integration tests. No migration was changed for this review. |
| Rate limits | Login/register/refresh use endpoint-specific in-memory limits and return HTTP 429 with `Retry-After` and a normalized error. | Configuration, interceptor, and Spring integration tests pass. Multi-instance/distributed rate limiting remains a deployment concern outside these WBS rows. |
| Secret and PII handling | Auth transport and session code contain no credential/token logging calls. API responses use generic auth errors and correlation IDs; request DTOs mark credentials as write-only. | Source scan found no auth-path console/logger/stdout calls. Device log capture and crash-report configuration were not available for inspection. |
| Data retention | Refresh token digests and lineage support reuse detection; design describes retaining rows through the validity/investigation window. | No cleanup job or approved post-expiry retention period is implemented. Define the operational retention window before adding cleanup. |
| Signing key handling | Local configuration loads persistent RSA keys; tracked config does not include private key material. | Key generation was not performed. Production key management remains a separate security review. |

The automated checks establish implementation behavior but do not substitute for the Master Plan's named Security Reviewer sign-off. WBS 1.2.10 remains **PARTIAL** pending that review and a retention-period decision.

## Integration review — WBS 1.2.11

### API origin and Login diagnostic

- `mobile/.env.local` currently sets `EXPO_PUBLIC_API_BASE_URL` to the Mac LAN origin on port 8080. The current `en0` address matched that origin during this check. The value stays out of tracked configuration.
- Expo iOS export successfully loaded `.env.local`; a byte scan confirmed the current API origin is present in the generated bundle. The temporary bundle was written outside the repository. Expo Metro serves the bundle on port 8081; it is not the API origin. Restart Metro with cache clear after changing it.
- The Java process was listening on port 8080. Docker Compose reported PostgreSQL on host port 5433 and MinIO on 9100; the PostgreSQL/Testcontainers suite also started an isolated PostgreSQL successfully.
- A single synthetic login request to the current LAN API origin returned `401 AUTH_INVALID_CREDENTIALS` and echoed the supplied safe correlation ID. This confirms reachability and normalized invalid-credential handling; it does not test a real account or a successful token response.
- The earlier iPhone message, “Dịch vụ đang gặp sự cố”, cannot be attributed conclusively to the old wrong origin or a current backend 500 from available evidence. The current `.env.local` value and API listener are correct, but no iPhone request trace, valid test account, or device log was available. The precise historical Login failure root cause is therefore **UNCONFIRMED**.

### Contract and failure matrix

| Path | Implemented behavior / evidence | Result |
|---|---|---|
| Login 200 | Mobile validates user and token pair, writes refresh token to SecureStore, keeps access token in memory, then routes to authenticated area. | Automated coverage passes; real valid-credential/device flow not run. |
| Login 400 / 401 / 403 / 429 / 500 | Transport preserves status, normalized code, field errors, correlation ID and `Retry-After`; UI maps known conditions to safe messages. | Mobile tests cover representative status/error mapping. Synthetic LAN check confirms 401 and correlation ID. |
| Network failure / timeout / cancellation | Distinct network, timeout and cancellation errors; login mutation does not retry automatically. | Mobile transport tests pass. No iPhone offline/recovery run. |
| Session restore / refresh | Neutral startup state, one refresh flight, rotation persistence, 401 clears session, network/429 preserves refresh credential. | Automated mobile and backend tests pass. No app-close/reopen device run. |
| Protected route | Route guard blocks entry until restoration; backend Spring Security requires authentication for protected routes. | Route/store tests and JWT MVC tests pass. |
| Logout | Mobile clears local storage and user cache. Backend has no `/api/v1/auth/logout` handler. OpenAPI labels that endpoint as a downstream implementation contract. | Local logout tests pass. Server-side revocation is **NOT IMPLEMENTED** and remains a dependency; this review does not claim it passes. |
| Stale async operation | A refresh or login result from an older session generation cannot replace a newer session; SecureStore mutations are serialized and stale writes are conditionally cleared. | Regression coverage verifies refresh completion after logout, queued logout cleanup, and preservation of a newer refresh token. |

WBS 1.2.11 remains **PARTIAL** until real-device session restore/network recovery are observed and the logout dependency is implemented under an authorized WBS. Do not mark iPhone E2E as passed from these automated results.

### iPhone Expo Go acceptance checklist — pending device run

1. **Login:** submit valid test-account credentials; confirm a complete user/token response, refresh-token SecureStore write, access-token memory state, and authenticated navigation. Do not capture the credentials or tokens.
2. **Session restore:** force-close Expo Go, relaunch, and confirm one Backend refresh completes before protected navigation, without Login flicker.
3. **Offline recovery:** after Login, force-close, disable Wi-Fi, and relaunch. Confirm a retryable recovery state and retained refresh credential; reconnect and retry to restore the session.
4. **Session invalidation:** present an invalid/revoked refresh token and confirm local session clear, Login navigation, and no refresh loop or protected route access.
5. **Logout:** confirm local access state, SecureStore data, and user query cache clear even offline. Verify server revocation only after the `/logout` endpoint is implemented and tested under its owning WBS.
6. **Error semantics:** exercise 401, 403, 429 with `Retry-After`, 500, network failure, and cancellation; confirm distinct safe UI handling and no raw credential in UI/logs.

## Verification record

- Backend `./mvnw -DforkCount=0 test`: **42 tests, 0 failures, 0 errors, 0 skipped** with `MAVEN_OPTS='-Dnet.bytebuddy.experimental=true'`. The flag is needed because this host uses Java 25 and the repository's Byte Buddy version officially supports through Java 23. Seven PostgreSQL/Testcontainers integration tests passed against isolated containers.
- Mobile `npm run typecheck`: passed.
- Mobile `npm run lint`: passed with no warnings.
- Mobile `npm test -- --runInBand`: **41 tests passed**, 8 suites passed, 0 failed.
- `npx expo export --platform ios --output-dir /private/tmp/lcp-expo-auth-check`: passed; `.env.local` loaded and the current API origin was found in the generated iOS bundle.
- Expo Doctor: not available as a local binary. `npx --no-install expo-doctor` did not complete within the available package/network environment and was stopped; report as **BLOCKED**, not passed.
- iPhone Expo Go flows: **NOT VERIFIED ON DEVICE**.
- Final `git diff --check`: passed.

## Local run notes

- Start infrastructure only with the repository's current `docker compose up -d postgres minio` configuration; do not add a backend container.
- Run Spring Boot directly from `backend` with `./mvnw spring-boot:run` after confirming the configured local database and existing key files.
- Set `EXPO_PUBLIC_API_BASE_URL=http://<MAC_LAN_IP>:8080` in ignored `mobile/.env.local`; never commit a developer LAN address. Start Expo from `mobile` with `npx expo start --clear`. Metro uses 8081, while Backend uses 8080.
- For device diagnosis, capture only API host/port, endpoint, HTTP status, normalized error code, correlation ID and error category. Never capture passwords, authorization headers, access/refresh tokens, or signing keys.
