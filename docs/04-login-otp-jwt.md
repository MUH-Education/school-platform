# 4. Login with phone number, OTP and JWT

## The idea in one example

Neelam is the office clerk. The owner added her on the Users and roles screen with only two things: phone `98123 40002` and role "Office admin".

1. Neelam opens the web app and types her phone number.
2. She gets a WhatsApp message: "Your school login code is 482913. It is valid for 5 minutes."
3. She types 482913.
4. The server gives her browser a token (the JWT). It is valid for 30 days.
5. For 30 days, every request carries this token. She does not see an OTP again until it ends.

There is no password anywhere in the system.

## Why this design

- A user is created with **phone + role only**. Nobody has to invent or remember a password.
- An OTP costs money each time. A 30-day token means about one OTP per person per month.
- The attendant logs in once and the phone stays logged in. This is what the phone app design needs.

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
4. If `attempts >= 5` → 429 `OTP_LOCKED`. The user must ask for a new code.
5. Hash the typed code and compare with `code_hash` using `MessageDigest.isEqual`.
6. Wrong → `attempts + 1`, answer 401 `OTP_INVALID`.
7. Right → set `consumed_at`, set `app_user.last_login_at`, write an `audit_log` row (LOGIN), build the JWT, answer 200:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-11-06T09:15:00+05:30",
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

### Step D. Use the token

Every other request sends `Authorization: Bearer <token>`.

The token (HS256) holds only:

| Claim | Example | Meaning |
|---|---|---|
| `sub` | `2` | user id |
| `role` | `OFFICE_ADMIN` | for information only |
| `ver` | `0` | the user's `token_version` when the token was made |
| `iat`, `exp` | | made at, ends at (30 days later) |

On each request the server:

1. Checks signature and expiry (Spring Security does this).
2. Loads the `app_user` row by `sub`.
3. Rejects with 401 if the user is not active, or `ver` is not equal to `token_version`.
4. Takes the role **from the database row**, not from the token. So a role change works at once.

This is one small query per request. With about 15 users, it costs nothing.

### Step E. Log out

`POST /api/v1/auth/logout` → `token_version + 1`. Every token of this user, on every device, stops working.

The same happens automatically when the owner turns a user off or changes their role.

## Limits

| Rule | Value | Why |
|---|---|---|
| Code length | 6 digits | |
| Code life | 5 minutes | |
| Wrong tries per code | 5 | A guess has a 5 in 1,000,000 chance |
| Wait before a new code | 60 seconds | Stops button mashing and cost |
| Codes per phone per hour | 5 | Stops someone spamming a staff phone |
| Codes per IP address per hour | 20 | Stops a script |

Count the per-phone and per-IP limits from `otp_code` rows (`created_at` in the last hour). No extra table and no Redis.

## Spring Security set-up

Use `spring-boot-starter-security-oauth2-resource-server`. It already knows how to read a Bearer JWT.

- `SecurityFilterChain`: stateless, CSRF off, `/api/v1/auth/otp/**` open, `/actuator/health` open, everything else needs a login.
- `JwtDecoder` bean: `NimbusJwtDecoder.withSecretKey(key)` with HS256.
- `JwtEncoder` bean: Nimbus encoder with the same secret key.
- Our own `Converter<Jwt, AbstractAuthenticationToken>`: does steps 2 to 4 of "Use the token" and sets authorities to `ROLE_<role>` plus each permission name.
- `@EnableMethodSecurity` so `@PreAuthorize("hasAuthority('STUDENTS_EDIT')")` works.
- `CurrentUser` helper: gives the logged-in `AppUser` to services.

## The first user

There is a chicken-and-egg problem: only an OWNER can add users, but at the start there is no user.

On start-up, if `app_user` is empty and `APP_OWNER_PHONE` is set, create one OWNER with that phone. Log one line: "Created first owner +91XXXXXX1234". Never do it again once a user exists.

## Changing a user's phone

The owner edits the phone on the Users and roles screen. The old phone can no longer log in. `token_version + 1`.

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
| 429 | `OTP_LOCKED` | 5 wrong tries on this code |
| 401 | `UNAUTHENTICATED` | missing, bad or old token on any other URL |

## Tests that prove it works

- Unknown phone and known phone get the same 200 answer; only the known phone creates an `otp_code` row.
- Right code → token; the same code a second time → 401.
- Code older than 5 minutes → 401 (use a fixed `Clock`).
- 5 wrong tries → 429; the right code after that → still 429.
- Second request inside 60 seconds → 429.
- Token of a user who was turned off → 401 on the next request.
- After logout, the old token → 401.
- The OTP never appears in any API response.
