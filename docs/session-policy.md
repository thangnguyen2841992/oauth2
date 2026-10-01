# Session policy

Updated 2026-09-27.

| Setting | USER (and STAFF) | ADMIN |
| --- | --- | --- |
| Access JWT | Up to 5 minutes | Up to 5 minutes |
| Absolute session / refresh JWT | 7 days from login | 8 hours from login |
| Idle timeout | No separate idle timeout | 30 minutes |

Access JWT expiry is also capped at the absolute/idle deadline. Both password and Google sign-in use the same HttpOnly, SameSite=Lax cookies. Cookie Max-Age is calculated from the token's remaining lifetime, including after refresh. Use COOKIE_SECURE=true when serving HTTPS.

The user-service keeps one session per user in process memory, with an absolute deadline, an admin idle deadline and refresh rotation state. Login replaces that session; logout removes only the session matching the presented signed token, and password setup/reset revokes the user's session. Refresh never increases the absolute or idle deadline. All sessions disappear when user-service restarts, so users must sign in again. Run only one user-service instance.

The refresh rotation uses an atomic per-user map operation. Simultaneous tabs or retries within a 10-second overlap receive the same successor token. During this overlap the current successor is not rotated again, preventing concurrent responses from replacing the browser cookie with an immediately invalid token. Reusing an older refresh token outside the overlap revokes the session. A lost response retried after the overlap can require sign-in again.

The gateway validates authenticated routed requests by calling user-service directly at `AUTH_SESSION_VALIDATION_URL` (default `http://127.0.0.1:8081/api/auth/session/validate`). Configure this URL if the two services run on different hosts. The endpoint verifies the signed access token against the in-memory session. Keep the user-service port private; the gateway denies the public route to this endpoint. If user-service is unavailable, protected requests return 503. Gateway rate limits also live in process memory, so run only one gateway instance for global limits.

Kafka defaults to the local broker at `127.0.0.1:9092` and topic auto-creation is enabled (`KAFKA_AUTO_CREATE_TOPICS=true`). Email activation needs this broker and the `send-email-active-response` topic. Set `KAFKA_AUTO_CREATE_TOPICS=false` only when the broker is unavailable and user-service still needs to start.

Only foreground pointer, keyboard, wheel or touch interactions send `/api/auth/activity`, throttled to once per minute. The server extends the admin idle deadline only if it has not already expired, capped by the original absolute deadline. Polling, WebSocket heartbeat, reconnect and refresh do not extend it. This endpoint is a client activity signal, not proof of human presence; an attacker controlling a valid session can imitate activity, but cannot extend the absolute deadline.

Database failures during login/refresh/checkLogin/activity return 503. The frontend keeps the session on transient refresh failures and redirects on confirmed 401. CheckLogin returns 401 for missing/expired access cookies so refresh can restore the session. Anonymous initial page loads stay on public pages.

## Applying the update

Deploy/restart `user-service` (8081), gateway (8082), and the frontend together. Existing pre-v2 sessions are not migrated or trusted: sign in again once. No database migration is required. Do not mix old and new authentication service versions during rollout. Environment overrides can change YAML defaults, so check deployment settings too.

Route browser API traffic through gateway. Backend service ports should be private; this gateway session-revocation check is not a replacement for network isolation of direct service endpoints. Already-open wallet WebSockets retain the existing JWT-expiry close behavior, so revocation may take up to the remaining access lifetime (at most 5 minutes) to close those connections.

## Verification

Backend unit tests cover JWT/cookie deadlines, refresh rejection, gateway idle/revocation enforcement and unavailable storage. FE tests cover concurrent refresh, 503 versus 401, initial cookie refresh, anonymous browsing and activity throttling.

`SessionMemoryTest` exercises concurrent rotation, replay revocation, deadlines, idle activity and stale logout without an external service. `SessionValidationTest` checks gateway validation and failure behavior; `InMemoryRateLimitTest` checks local throttling.
