# Session policy

Updated 2026-10-10. Deployment instructions: [gateway-security-hardening.md](../../docs/gateway-security-hardening.md).

| Setting | USER (and STAFF) | ADMIN |
| --- | --- | --- |
| Access JWT | Up to 5 minutes | Up to 5 minutes |
| Absolute session / refresh JWT | 7 days from login | 8 hours from login |
| Idle timeout | No separate idle timeout | 30 minutes |

Access JWT expiry is also capped at the absolute/idle deadline. Both password and Google sign-in use the same HttpOnly, SameSite=Lax cookies. Cookie Max-Age is calculated from the token's remaining lifetime, including after refresh. Use COOKIE_SECURE=true when serving HTTPS.

The user-service keeps one session per user in the shared SQL `auth_session` table, with an absolute deadline, an admin idle deadline and refresh rotation state. Login replaces that session; logout removes only the session matching the presented signed token, and password setup/reset revokes the user's session. Refresh never increases the absolute or idle deadline. Sessions survive restarts and multiple auth instances share the same store and encryption key. The refresh token stored in the row is encrypted with AES-GCM; current and previous hashes identify replay.

The refresh rotation uses an InnoDB per-user row lock inside a transaction. Simultaneous tabs or retries within a 10-second overlap receive the same successor token. During this overlap the current successor is not rotated again, preventing concurrent responses from replacing the browser cookie with an immediately invalid token. Reusing an older refresh token outside the overlap revokes the session. A lost response retried after the overlap can require sign-in again.

The gateway and direct backend requests validate sessions through user-service at `auth.session-validation-url` (default `http://127.0.0.1:8081/api/auth/session/validate`). The endpoint requires a separate internal credential and verifies the signed access token against the persisted session and current user role/active state. Keep the user-service port private; the gateway denies the public route to this endpoint. Production requires HTTPS when auth runs on another host. If user-service is unavailable, protected requests return 503. Gateway rate limits are persisted in `auth_rate_bucket` and shared by gateway instances.

Kafka defaults to the local broker at `127.0.0.1:9092` and topic auto-creation is enabled (`KAFKA_AUTO_CREATE_TOPICS=true`). Email activation needs this broker and the `send-email-active-response` topic. Set `KAFKA_AUTO_CREATE_TOPICS=false` only when the broker is unavailable and user-service still needs to start.

Only foreground pointer, keyboard, wheel or touch interactions send `/api/auth/activity`, throttled to once per minute. The server extends the admin idle deadline only if it has not already expired, capped by the original absolute deadline. Polling, WebSocket heartbeat, reconnect and refresh do not extend it. This endpoint is a client activity signal, not proof of human presence; an attacker controlling a valid session can imitate activity, but cannot extend the absolute deadline.

Database failures during login/refresh/checkLogin/activity return 503. The frontend keeps the session on transient refresh failures and redirects on confirmed 401. CheckLogin returns 401 for missing/expired access cookies so refresh can restore the session. Anonymous initial page loads stay on public pages.

## Applying the update

Apply [gateway-security-migration.sql](../../docs/gateway-security-migration.sql), provision RSA/internal/encryption keys, and deploy/restart user-service, gateway, staff, nihongo-user, admin and frontend together. Old HS256 tokens and in-memory sessions are not migrated: sign in again once. Do not mix old and new authentication service versions during rollout. Production uses explicit key paths and Secure cookies; local HTTP remains the default profile.

Route browser HTTP and socket traffic through gateway. Backend service ports should be private even with direct session verification enabled. Auth, wallet and monitoring sockets retain JWT-expiry close behavior and now check revocation in batches every 500 ms, with a bounded network timeout. Revoked/unverifiable connections are removed from the outbound registry even when transport close fails.

## Verification

Backend unit tests cover JWT/cookie deadlines, refresh rejection, gateway idle/revocation enforcement and unavailable storage. FE tests cover concurrent refresh, 503 versus 401, initial cookie refresh, anonymous browsing and activity throttling.

`SessionMemoryTest` retains the pure in-memory unit fixture for deadline/rotation logic. Runtime uses the autowired SQL constructor. `SharedSecurityIntegrationTest` checks real local HTTP, RSA tokens, shared SQL sessions and concurrent refresh/quota. `SecurityHardeningTest`, `GatewayHardeningTest` and `PublicCoursesSecurityTest` check the protections enabled. Older business fixtures explicitly opt into legacy JWT mode for regression tests only; production rejects that mode.
