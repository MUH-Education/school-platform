# Security rules

These rules hold for every feature and every phase. `CLAUDE.md` has the short list. This file has the detail and an example for each rule.

## 1. The server checks permission on every request

- Every controller method has `@PreAuthorize("hasAuthority('<PERMISSION>')")`, or is "any login" (needs only a valid token), or is in the short list of open URLs.
- Open URLs: `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify`, `GET /actuator/health` (and its `liveness` / `readiness` parts). Nothing else.
- **One exception, for the API docs:** `/v3/api-docs/**` and `/swagger-ui/**` are open **only while the docs are switched on** (`springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are true: `local` and `test`). They are false by default and in `prod`, and then these two paths are not open (no token → 401, with a token → 404). The docs are only a page: every API call made from Swagger still needs a real token and still gets 401 or 403 as normal. Never add another open URL.
- Check **permissions**, never roles. Example: `hasAuthority('VEHICLES_VIEW')`, not `hasRole('OWNER')`.
- Hiding a button in React is not security. The React app gets the permission list only to hide menu items.

## 2. Never trust an id from the client

- If the request names something the user may touch only when it is "theirs", look it up in the database and compare.
- Example: an attendant sends `routeId=7`. The server finds the attendant's own route today. If it is route 4, answer 403 `NOT_YOUR_ROUTE` and save nothing.
- The logged-in user comes from the token (`CurrentUser`), never from a `userId` in the body.

## 3. Tokens and sessions

- One JWT (HS256) per login session. Claims: `sub` (user id, for audit only), `sid` (session id), `iat`, `exp`.
- `exp` is the session's 12-hour hard cap (`app.session.max-lifetime`), not the thing that decides "is this session still good" on its own — see below.
- A session is made (Postgres `user_session` row, hashed id like an OTP code; plus a Redis `session:<id>` entry) only when an OTP is checked and right.
- On every other request the server resolves the session: Redis first — this is the normal path, no database call. Only if Redis has forgotten it (restart, eviction) does the server fall back to the `user_session` row, reload the user, and check `active` there (the Postgres row itself has no idea about idle time, only "revoked?" and "past the hard cap?").
- A session slides: every request that resolves it pushes its 15-minute idle deadline (`app.session.idle-timeout`) forward, capped at the 12-hour hard limit from login. Idle past 15 minutes, or past the 12-hour cap no matter how active → 401, and this is checked for certain from the Redis entry itself — it never falls back to Postgres for an idle session, because Postgres cannot tell idle time at all and would wrongly say "still fine".
- The role used for permissions comes from the session (set once at login from the database row, refreshed on the rare Postgres-fallback path), not from a claim inside the token.
- Logout ends only that one session — other devices of the same user keep working. Turn off, role change, phone change end **every** session of that user at once (`SessionService.revokeAll`).

## 4. OTP

- Codes come from `SecureRandom`. They are stored only as HMAC-SHA256 with `app.otp.hash-secret`, in Redis (not Postgres — docs/08-decisions.md). Compare with `MessageDigest.isEqual`.
- The answer to `otp/request` is the same for a known and an unknown phone.
- `otp/verify` gives the same `OTP_INVALID` for: no code, old code, wrong code, unknown user, turned-off user.
- Limits: 3 wrong tries per code, 60 seconds between codes, 5 codes per phone per hour, 20 per IP per hour.
- An OTP never appears in an API response.

## 5. Never log secrets or full phone numbers

- Never log an OTP, a JWT, or a secret.
- Log phones masked: `+91XXXXXX4321`.
- The one exception: `LogOtpSender` prints the code, and it is only for `local`. In `prod` the app refuses to start if `log` is the only OTP channel.

## 6. Secrets come from environment variables

- `APP_JWT_SECRET` (at least 32 bytes), `APP_OTP_SECRET`, `APP_SESSION_SECRET`, provider keys, database password, Redis password (if the server needs one).
- `prod` has no default for any secret. If one is missing, the app does not start.
- `local` and `test` may have a fixed, clearly marked "not for production" value (in `application-local.properties` and the `test` block of `application.yml`). It must never be used in `prod`.

## 7. Errors do not leak

- 401 → `{"error":"UNAUTHENTICATED",...}`, 403 → `{"error":"FORBIDDEN",...}`. Security writes these bodies itself.
- A crash → 500 `INTERNAL_SERVER_ERROR` with a plain message. The details go only to the log.

## 8. Tests

- Every endpoint has a test for "no token → 401" and "wrong role → 403".
- No test sends a real SMS or WhatsApp message.

## Attendant scope

- An `ATTENDANT` user has `TRIPS_RECORD` only, and a `staff_id`.
- Every trip endpoint finds the attendant's route today (user → `staff_id` → vehicle assignment today → active route of that vehicle).
- The request must be about that route, or about a child who is on that route today. Otherwise 403 `NOT_YOUR_ROUTE`, and nothing is saved.
- A user with `TRIPS_RECORD_ANY` skips this check (the office correcting a wrong tap).
- The attendant never sees the student list or parents' numbers (question C10 default).
