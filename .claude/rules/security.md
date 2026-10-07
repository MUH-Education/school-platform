# Security rules

These rules hold for every feature and every phase. `CLAUDE.md` has the short list. This file has the detail and an example for each rule.

## 1. The server checks permission on every request

- Every controller method has `@PreAuthorize("hasAuthority('<PERMISSION>')")`, or is "any login" (needs only a valid token), or is in the short list of open URLs.
- Open URLs: `POST /api/v1/auth/otp/request`, `POST /api/v1/auth/otp/verify`, `GET /actuator/health` (and its `liveness` / `readiness` parts). Nothing else.
- Check **permissions**, never roles. Example: `hasAuthority('VEHICLES_VIEW')`, not `hasRole('OWNER')`.
- Hiding a button in React is not security. The React app gets the permission list only to hide menu items.

## 2. Never trust an id from the client

- If the request names something the user may touch only when it is "theirs", look it up in the database and compare.
- Example: an attendant sends `routeId=7`. The server finds the attendant's own route today. If it is route 4, answer 403 `NOT_YOUR_ROUTE` and save nothing.
- The logged-in user comes from the token (`CurrentUser`), never from a `userId` in the body.

## 3. Tokens

- One JWT (HS256), 30 days. Claims: `sub` (user id), `role` (information only), `ver` (`token_version`), `iat`, `exp`.
- On every request the server loads the `app_user` row. Not active, or `ver` is not equal to `token_version` → 401.
- The role used for permissions comes **from the database row**, not from the token.
- Logout, turn off, role change, phone change → `token_version + 1`.

## 4. OTP

- Codes come from `SecureRandom`. They are stored only as HMAC-SHA256 with `app.otp.hash-secret`. Compare with `MessageDigest.isEqual`.
- The answer to `otp/request` is the same for a known and an unknown phone.
- `otp/verify` gives the same `OTP_INVALID` for: no code, old code, wrong code, unknown user, turned-off user.
- Limits: 5 wrong tries per code, 60 seconds between codes, 5 codes per phone per hour, 20 per IP per hour.
- An OTP never appears in an API response.

## 5. Never log secrets or full phone numbers

- Never log an OTP, a JWT, or a secret.
- Log phones masked: `+91XXXXXX4321`.
- The one exception: `LogOtpSender` prints the code, and it is only for `dev` and `test`. In `prod` the app refuses to start if `log` is the only OTP channel.

## 6. Secrets come from environment variables

- `APP_JWT_SECRET` (at least 32 bytes), `APP_OTP_SECRET`, provider keys, database password.
- `prod` has no default for any secret. If one is missing, the app does not start.
- `dev` and `test` may have a fixed, clearly marked "dev only" value in `application.yml`. It must never be used in `prod`.

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
