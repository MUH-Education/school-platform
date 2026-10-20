# 4. Login with phone number, OTP and JWT

## The idea in one example

Neelam is the office clerk. The owner added her on the Users and roles screen with only two things: phone `98123 40002` and role "Office admin".

1. Neelam opens the web app and types her phone number.
2. She gets a WhatsApp message: "Your school login code is 482913. It is valid for 5 minutes."
3. She types 482913.
4. The server gives her browser a token (the JWT) and makes a **session** for it.
5. Every request carries this token. As long as she keeps using it, the session stays alive — it slides forward 15 minutes on every request. If she walks away for more than 15 minutes, it ends and she needs a new OTP. It can never last past 12 hours from the moment she logged in, however active she stays.

There is no password anywhere in the system.

## Why this design

- A user is created with **phone + role only**. Nobody has to invent or remember a password.
- A session that slides with use means someone actively working (an attendant on a route, a clerk at a desk) is never bothered mid-task, but a phone left on a desk, or a stolen token, goes stale within 15 minutes of the last real use.
- 12 hours comfortably covers one school day (morning run to evening run) without ever letting a session live forever just because something kept pinging it.
- See docs/08-decisions.md for why this replaced the earlier "one 30-day token, checked against the database every request" design, and why OTP state moved from Postgres to Redis.

## Step by step

### Step A. Ask for a code

`POST /api/v1/auth/otp/request`

```json
{ "phone": "98123 40002" }
```

What the server does:

1. Normalize the phone → `+919812340002`. If it is not a valid Indian mobile number → 400 `VALIDATION`.
2. Check the limits (see "Limits" below). If over → 429 `OTP_TOO_MANY_REQUESTS` with `retryAfterSeconds`.
3. Look for an **active** `app_user` with this phone.
4. If found: make a 6-digit code with `SecureRandom`, save its hash in `otp_code`, send the code.
5. If **not** found: do nothing more. Do not send anything.
6. In both cases answer **the same** 200:

```json
{ "message": "If this number is registered, a code has been sent.", "expiresInSeconds": 300, "resendAfterSeconds": 60 }
```

Why the same answer? So a stranger cannot test which phone numbers work at the school.

### Step B. Send the code

The sender tries the channels in the order of `app.otp.channels`.

| Order | Channel | When it is used |
|---|---|---|
| 1 | WhatsApp | First try. Cheap, and staff read WhatsApp. |
| 2 | SMS | If WhatsApp fails (provider error), send by SMS. |
| dev | `log` | On your laptop: prints `OTP for +91XXXXXX0002 is 482913` in the console. No real message. |

One interface, three classes:

```java
public interface OtpSender {
    String channel();                       // "WHATSAPP", "SMS", "LOG"
    void send(String phone, String code) throws OtpSendException;
}
```

`OtpDeliveryService` holds the list and tries them in order. The first one that does not throw wins. The channel used is saved in `otp_code.channel`.

### Step C. Check the code

`POST /api/v1/auth/otp/verify`

```json
{ "phone": "98123 40002", "otp": "482913" }
```

What the server does:

1. Normalize the phone.
2. Load the newest `otp_code` row for this phone that is not consumed.
3. Fail with 401 `OTP_INVALID` if: no row, or expired, or the user is missing or turned off. **Same error for all**, so nothing leaks.
4. If `attempts >= 3` → 429 `OTP_LOCKED`. The user must ask for a new code.
5. Hash the typed code and compare with the stored hash using `MessageDigest.isEqual`.
6. Wrong → `attempts + 1`, answer 401 `OTP_INVALID`.
7. Right → the code is deleted from Redis (cannot be used twice), set `app_user.last_login_at`, write an `audit_log` row (LOGIN), make a session and build the JWT for it, answer 200:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-10-07T21:00:00+05:30",
  "user": {
    "id": 2,
    "name": "Neelam",
    "phone": "+919812340002",
    "role": "OFFICE_ADMIN",
    "permissions": ["BUS_STATUS_VIEW", "STUDENTS_VIEW", "STUDENTS_EDIT"],
    "route": null
  }
}
```

For an attendant, `route` is filled: `{ "id": 4, "name": "Route 4", "vehicle": "Van 4" }`. If the attendant has no vehicle today, `route` is null and the phone app shows "No route today. Call the office."

`expiresAt` is the session's 12-hour hard cap, not a promise that it will last that long — normal use (tapping, browsing) keeps the session alive on its own, well short of this; it is simply the latest moment the session could ever still be valid, no matter how active the user stayed.

### Step D. Use the token

Every other request sends `Authorization: Bearer <token>`.

The token (HS256) holds only:

| Claim | Example | Meaning |
|---|---|---|
| `sub` | `2` | user id, kept for audit and logging only |
| `sid` | `k3f9...` | the session id |
| `iat`, `exp` | | made at, the session's 12-hour hard cap |

On each request the server resolves the session by `sid`:

1. Checks the token's signature and its own (12-hour) expiry (Spring Security does this).
2. Asks Redis for `session:<sid>` — the normal path, no database call. If found and still inside its 15-minute idle window and the 12-hour cap, this answers the request: who the user is, their role, their staff id if any. The session's idle deadline is pushed another 15 minutes forward.
3. If Redis has forgotten the session (restart, eviction — rare), falls back to the `user_session` row in Postgres: not revoked, and still inside the 12-hour cap from when it was made. If good, the `app_user` row is reloaded once, checked for `active`, and the session is written back into Redis so the next request is fast again.
4. Anything that fails any of this → 401 `UNAUTHENTICATED`, the same answer for an idle session, a revoked one, a turned-off user, or no token at all.

The role used for permissions comes from the session (filled in from the database at login, or refreshed on the rare Postgres-fallback path above) — not from a claim baked into the token, so a role change or turning a user off can end their sessions at once rather than waiting for the token to expire on its own.

### Step E. Log out

`POST /api/v1/auth/logout` ends **only the session this token belongs to**. Other devices of the same user are not affected — logging out your laptop does not log out your phone.

Turning a user off, or changing their role or phone number, ends **every** session of that user at once (not just the current one) — the same as before, just done by revoking sessions instead of bumping a version number.

## Limits

| Rule | Value | Why |
|---|---|---|
| Code length | 6 digits | |
| Code life | 5 minutes | |
| Wrong tries per code | 3 | A guess has a 1 in 1,000,000 chance; 3 tries is enough for a mistyped digit, not for guessing |
| Wait before a new code | 60 seconds | Stops button mashing and cost |
| Codes per phone per hour | 5 | Stops someone spamming a staff phone |
| Codes per IP address per hour | 20 | Stops a script |
| Session idle timeout | 15 minutes | Slides forward on every request; only an idle session times out |
| Session hard cap | 12 hours | However active, a session never outlives one school day from login |

The code, its wrong-try count, and the per-phone/per-IP windows above all live in Redis, not Postgres (docs/08-decisions.md) — a TTL-based key is a natural fit for something that is only ever meant to last a few minutes to an hour.

## Spring Security set-up

Use `spring-boot-starter-security-oauth2-resource-server`. It already knows how to read a Bearer JWT.

- `SecurityFilterChain`: stateless, CSRF off, `/api/v1/auth/otp/**` open, `/actuator/health` open, everything else needs a login.
- `JwtDecoder` bean: `NimbusJwtDecoder.withSecretKey(key)` with HS256.
- `JwtEncoder` bean: Nimbus encoder with the same secret key.
- Our own `Converter<Jwt, AbstractAuthenticationToken>` (`UserJwtConverter`): resolves the session by `sid` (Redis, then the Postgres fallback) and sets authorities to `ROLE_<role>` plus each permission name.
- `@EnableMethodSecurity` so `@PreAuthorize("hasAuthority('STUDENTS_EDIT')")` works.
- `CurrentUser` helper: gives the logged-in user's id, role, staff id and session id to services, from a `RequestContext` filled in once per request.

## The first user

There is a chicken-and-egg problem: only an OWNER can add users, but at the start there is no user.

On start-up, if `app_user` is empty and `APP_OWNER_PHONE` is set, create one OWNER with that phone. Log one line: "Created first owner +91XXXXXX1234". Never do it again once a user exists.

## Changing a user's phone

The owner edits the phone on the Users and roles screen. The old phone can no longer log in, and every session this user had ends at once.

## What to ask the provider

WhatsApp and SMS both need setting up with a provider before real codes can go out. This takes days, so the code is built against the `log` sender first (Phase 1) and the real providers are added in Phase 5.

- **WhatsApp:** a WhatsApp Business account through a provider, and one approved **authentication template** (a fixed text with the code and a "copy code" button).
- **SMS in India:** DLT registration of the school, a sender id, and one approved **OTP template**.

## Errors of this module

| HTTP | `error` | When |
|---|---|---|
| 400 | `VALIDATION` | phone is not a valid mobile number, OTP is not 6 digits |
| 401 | `OTP_INVALID` | wrong code, expired code, unknown or turned-off user |
| 429 | `OTP_TOO_MANY_REQUESTS` | asked too often |
| 429 | `OTP_LOCKED` | 3 wrong tries on this code |
| 401 | `UNAUTHENTICATED` | missing, bad or old token, or an idle/ended session, on any other URL |

## Tests that prove it works

- Unknown phone and known phone get the same 200 answer; only the known phone gets a code saved in Redis.
- Right code → token; the same code a second time → 401.
- Code older than 5 minutes → 401 (use a fixed `Clock`).
- 3 wrong tries → 429; the right code after that → still 429.
- Second request inside 60 seconds → 429.
- Token of a user who was turned off → 401 on the next request.
- After logout, the old token → 401, but a second device's token for the same user still works.
- A session idle for more than 15 minutes → 401; one used every few minutes keeps working for hours, but never past 12 hours from login.
- The OTP never appears in any API response.
