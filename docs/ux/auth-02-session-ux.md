# AUTH-02 Session UX Specification

**WBS:** 1.2.2  
**Master Plan task:** `[UX] Thiết kế/đối chiếu UX cho AUTH-02 trên Auth session; bao phủ loading, empty, error, retry, permission/offline nếu liên quan`  
**Status:** Design complete. Mobile session runtime and protected navigation remain downstream implementation work.

## Approved session rules

- Access credentials are RS256 JWTs with a 900-second lifetime. Refresh credentials are opaque, rotate on every successful refresh, and expire after 7 days.
- Keep access tokens in memory where possible and refresh tokens in Expo SecureStore. Never log credentials or report them in UI.
- A refresh operation is single-flight: simultaneous requests wait on the same operation. A successfully rotated refresh token replaces the old pair atomically in SecureStore.
- Reuse of a rotated token revokes its refresh-token family. A refresh 401 clears local session data and requires login. Logout revokes the refresh session/token and clears local session data.
- A logout/revocation does not immediately invalidate an already issued access JWT. It may remain usable for up to its remaining 15-minute lifetime.

## Navigation and startup

1. Start in a neutral splash/restoring state. Do not render protected screens or protected data until session restoration resolves; this prevents an unauthenticated navigation flicker.
2. If SecureStore has no refresh token, clear any orphan access-token memory state and route to Login/Register.
3. If a refresh token exists, refresh once before entering authenticated routes. On success, keep the returned access token in memory and persist the replacement refresh token, then enter the authenticated route tree.
4. If restoration fails because the device is offline or the request is rate limited, retain the refresh token, remain outside protected routes, and show a retryable offline/wait state. Do not erase credentials for a transport error or 429.
5. If the server returns 401 for refresh, including expired, revoked, malformed, or reused credentials, clear local tokens and route to Login. Use a generic message that does not disclose token or session status.
6. The existing app has no protected destination or startup guard. Until a protected route is implemented, the successful authenticated destination is a navigation integration dependency; do not invent a screen in this design task.

## State transition matrix

| State/event | Trigger | UI/message | Retry policy | Navigation | Token/storage action |
|---|---|---|---|---|---|
| No session | No refresh token in SecureStore | Login/Register; no authenticated indicator | User submits login | Auth routes | Clear orphan access token from memory; no write |
| Login loading | Login request pending | Keep form visible, show progress, prevent duplicate submit | No automatic retry | Stay on Login | Do not persist credentials or partial response |
| Login success | 200 with non-null user and complete token pair; storage succeeds | Show success/continue | No retry | Enter authenticated routes only after storage succeeds | Access in memory; refresh token in SecureStore |
| Login 400 | Local or API validation failure | Field-level validation; preserve unrelated form input | Correct fields and explicitly submit | Stay on Login | No session created |
| Login 401 | Invalid credentials | Generic “email or password is incorrect” message | Explicit user retry; no automatic retry | Stay on Login | No session created; never indicate whether email exists |
| Login 403 | Account unavailable/operation forbidden | Safe account status message | Retry only after user action or account state changes | Stay on Login | No session created |
| Login 429 | Rate limit; `Retry-After` supplied | Show wait duration and disable submit until allowed | Retry after header delay; no automatic request loop | Stay on Login | No session created |
| Login network/offline | No response/transport failure | Explain connection problem; preserve email and password in form memory | Explicit retry when online; do not automatically replay credential POST | Stay on Login | No session created; no credential persistence |
| Invalid login response | Missing user/token field or malformed token pair | Safe service error; do not report success | Explicit retry | Stay on Login | Delete partially written keys; remain unauthenticated |
| SecureStore write failure | Persisting token pair fails | Safe session-save error | User may retry login | Stay on Login | Roll back both keys; never fall back to AsyncStorage |
| Session restoring | App startup finds refresh token | Neutral splash/progress; no protected content | One refresh attempt | Keep outside protected routes | Read refresh token; do not expose it in UI/logs |
| Session active | Access token is valid in memory | Render authenticated route tree | Refresh only when needed | Authenticated routes | Access in memory; refresh in SecureStore |
| Access token expired/near expiry | API returns 401 due to expiry or expiry timer | Usually keep current screen while refresh runs | Join single-flight refresh; replay an eligible request at most once | No route change until refresh outcome | Keep current refresh token until rotation result is stored |
| Refresh loading | One refresh in progress | Keep current screen or neutral startup state; avoid duplicate spinner/navigation | All callers await same refresh promise | No duplicate navigation | Only one refresh request; do not erase token on network/429 |
| Refresh success | Refresh 200 with complete replacement pair | Continue current task without visible login interruption | No further retry | Remain authenticated | Replace refresh token securely; update access token in memory atomically |
| Refresh 401/expired/revoked | Server rejects refresh token | Explain session ended and ask user to sign in again | No refresh retry | Clear protected navigation and show Login | Clear access and refresh tokens |
| Refresh reuse detected | A rotated token is presented; server revokes family and responds generic 401 | Same generic expired-session message | No retry with the rejected token | Show Login | Clear both local tokens; do not disclose family/reuse details |
| Refresh network/offline | No response/transport error | Keep user informed; offer retry when connected | Explicit retry or connectivity-triggered single retry with backoff; never parallel refresh | Stay outside protected routes during startup; preserve current route when already active if safe | Preserve refresh token; no token rotation assumed |
| Refresh 429 | Refresh abuse protection throttles request | Wait message; honor `Retry-After` | Retry after delay, one single-flight operation | Preserve current route if access JWT is still valid; otherwise gate protected data | Retain refresh token; do not clear session |
| SecureStore read/write failure | Storage unavailable/corrupt | Safe session unavailable message | Explicit retry; avoid repeated background loop | Do not enter protected routes without recoverable session | Never claim authenticated; clear partial pair only if safely possible |
| Protected API 403 | Authenticated token lacks resource permission | Show access-denied state; preserve user context | No auth refresh loop; retry only after permissions change | Stay in app; return to prior safe route if needed | Keep session and tokens |
| Logout | User confirms/signals logout | Show progress, then signed-out state | Remote revoke may be retried; do not block local sign-out | Route to Login immediately after local clear | Send refresh token to revoke; clear access memory and SecureStore regardless of network result |
| Logout offline/server error | Revocation request cannot complete | Complete local sign-out; show no secret detail | Best-effort remote retry only if a safe queued design is later approved; never retain plaintext token for that queue | Login | Clear local tokens; server refresh may remain valid until expiry unless revoke reached server |

## Flow summaries

### Login

```text
Login form → validate locally → POST login
  ├─ 400: field feedback; no session
  ├─ 401/403: safe message; no session
  ├─ 429: wait for Retry-After; no automatic retry
  ├─ network error: preserve form; explicit retry; no session
  └─ 200: validate complete response → write refresh to SecureStore
       ├─ storage/response invalid: rollback partial data; remain signed out
       └─ success: hold access in memory → authenticated navigation
```

### Restore and refresh

```text
App start → neutral splash → read refresh token
  ├─ absent: Login
  └─ present: one single-flight POST refresh
       ├─ 200: atomically replace token pair → authenticated navigation
       ├─ 401: clear tokens → Login
       ├─ offline/5xx/429: preserve refresh token → retryable, unauthenticated startup state
       └─ SecureStore failure: do not claim authenticated; recover/retry safely

Protected request 401 → await shared refresh
  ├─ success: replay eligible request once
  ├─ 401: clear tokens → Login
  └─ offline/429: preserve tokens and show retry/wait state
```

### Logout

```text
User logout → POST logout with refresh token (best effort)
            → clear access memory + SecureStore immediately
            → clear protected navigation state → Login
```

The logout API contract is idempotent and does not reveal whether the supplied refresh token existed. If the request fails, local logout still completes; the server-side refresh credential may remain usable until expiry. An access JWT already issued may remain accepted for up to its remaining TTL.

## Error and privacy rules

- Treat 401 as authentication failure only for endpoints requiring authentication. An invalid login is not a stored-session failure.
- Treat 403 as an authorization failure, not a reason to clear a valid session.
- Honor `Retry-After` for 429 and avoid retry storms. Never automatically retry login POST.
- For an expired access token, refresh once and replay only requests that are safe to replay; mutation replay requires an idempotency strategy from the calling API.
- Do not request OS permissions for login or session management.
- Never persist passwords/confirmation passwords, log credentials/tokens/secrets, or put them in analytics/crash breadcrumbs.
- Do not infer logout success from a failed network call; local sign-out is immediate while remote revocation is best effort.

## Mobile implementation acceptance checklist

- [ ] Startup uses a neutral restoring state and never flashes protected content before restore finishes.
- [ ] Missing refresh token goes to Login without attempting refresh.
- [ ] Login success requires non-null user and complete token pair plus successful SecureStore write.
- [ ] Access token is held in memory; refresh token is in SecureStore; writes are atomic/rolled back on failure.
- [ ] Concurrent 401s share one refresh; an eligible original request is replayed at most once.
- [ ] Refresh 401 clears the session; offline/429 does not clear it; 403 preserves it.
- [ ] Login network error preserves form input without automatic credential replay.
- [ ] 429 honors `Retry-After` for login and refresh.
- [ ] Logout clears local state even when remote revoke fails and does not claim access JWT is instantly revoked.
- [ ] No token, password, or signing secret reaches logs, analytics, or UI text.
- [ ] Tests cover restoring, refresh single-flight, rotation persistence, offline, storage errors, 401, 403, 429 and logout.

## Current implementation and dependencies

- Login form/mutation already validates a complete pair and stores token material through SecureStore, but the current Mobile types allow `LoginResponse.tokens` to be null; the contract type is being aligned.
- Current storage persists both tokens in SecureStore. The approved target prefers access token in memory and refresh token in SecureStore; adjust as part of Mobile session runtime work.
- There is no startup restore guard, authenticated route destination, refresh client, or logout client yet. These are implementation dependencies for later Mobile WBS tasks, not blockers for this UX design deliverable.
- Backend refresh/logout runtime availability is tracked under the subsequent backend implementation tasks. The UX contract does not claim that the runtime is already complete.
