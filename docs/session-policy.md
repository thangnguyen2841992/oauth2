# Session policy

Updated 2026-09-27.

| Setting | USER (and STAFF) | ADMIN |
| --- | --- | --- |
| Access JWT | Up to 5 minutes | Up to 5 minutes |
| Absolute session / refresh JWT | 7 days from login | 8 hours from login |
| Idle timeout | No separate idle timeout | 30 minutes |

Access JWT expiry is also capped at the absolute/idle deadline. Both password and Google sign-in use the same HttpOnly, SameSite=Lax cookies. Cookie Max-Age is calculated from the token's remaining lifetime, including after refresh. Use COOKIE_SECURE=true when serving HTTPS.

Redis stores one session per user under `auth:session:v2:<UUID>`, with an absolute TTL, an admin idle deadline and refresh rotation state. Login replaces that session; logout removes only the session matching the presented signed token, and password setup/reset revokes the user's session. Refresh never increases the absolute TTL or idle deadline. The gateway validates session ID and deadlines on authenticated routed requests. Auth endpoints validate their own cookies.

The refresh rotation uses a single atomic Redis Lua script. Simultaneous tabs or retries within a 10-second overlap receive the same successor token. During this overlap the current successor is not rotated again, preventing concurrent responses from replacing the browser cookie with an immediately invalid token. Reusing an older refresh token outside the overlap revokes the session. A lost response retried after the overlap can require sign-in again. Redis contains the current refresh token so that retries can receive the identical successor; restrict Redis access to trusted services.

Only foreground pointer, keyboard, wheel or touch interactions send `/api/auth/activity`, throttled to once per minute. The server extends the admin idle deadline only if it has not already expired, capped by the original absolute deadline. Polling, WebSocket heartbeat, reconnect and refresh do not extend it. This endpoint is a client activity signal, not proof of human presence; an attacker controlling a valid session can imitate activity, but cannot extend the absolute deadline.

Redis/database failures during login/refresh/checkLogin/activity return 503. The frontend keeps the session on transient refresh failures and redirects on confirmed 401. CheckLogin returns 401 for missing/expired access cookies so refresh can restore the session. Anonymous initial page loads stay on public pages.

## Applying the update

Deploy/restart `user-service` (8081), gateway (8082), and the frontend together. Existing pre-v2 sessions are not migrated or trusted: sign in again once. No database migration is required. Do not mix old and new authentication service versions during rollout. Environment overrides can change YAML defaults, so check deployment settings too.

Route browser API traffic through gateway. Backend service ports should be private; this gateway session-revocation check is not a replacement for network isolation of direct service endpoints. Already-open wallet WebSockets retain the existing JWT-expiry close behavior, so revocation may take up to the remaining access lifetime (at most 5 minutes) to close those connections.

## Verification

Backend unit tests cover JWT/cookie deadlines, refresh rejection, gateway idle/revocation enforcement and unavailable storage. FE tests cover concurrent refresh, 503 versus 401, initial cookie refresh, anonymous browsing and activity throttling.

`SessionRedisTest` runs only when `AUTH_TEST_REDIS_PORT` is set. It connects solely to 127.0.0.1, uses random test user IDs and cleans its own keys. Use a disposable local Redis, never production. It exercises the actual Lua scripts, including 20 concurrent rotations, replay revocation, TTL limits, idle activity and stale logout protection.

Example from workspace root after starting a disposable local Redis on port 16379:

```powershell
$env:AUTH_TEST_REDIS_PORT = '16379'
& 'C:/Users/Admin/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn.cmd' -o -B -ntp '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' -f project-verification-pom.xml -pl user,nihongo-gateway -am '-Dtest=Auth*Test,TokenLifetimeTest,SessionRedisTest,SessionValidationTest,PasswordSetupTest,UserPrivacyTest,PublicCoursesSecurityTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
Remove-Item Env:AUTH_TEST_REDIS_PORT
```
