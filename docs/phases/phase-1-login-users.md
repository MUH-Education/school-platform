# Phase 1 — Login, users, roles

## Goal

A person whose phone number is registered can log in with an OTP and gets a token. The owner can add users with a phone number and a role. Every URL checks permission.

## Screens this makes work

- Web login and **M1. Login** (phone app)
- **8. Users and roles**

## Read first

- `docs/04-login-otp-jwt.md` (the whole flow)
- `docs/05-roles-permissions.md`
- `docs/03-data-model.md` → Phase 1 tables
- `.claude/rules/security.md`

## Tables

`V1__auth_users.sql`: `app_user`, `otp_code`, `audit_log`, `app_setting` (with the start values).

Note: `app_user.staff_id` is a plain `bigint` now. Its foreign key comes in Phase 2, when the `staff` table exists.

## Endpoints

| Method and URL | Permission |
|---|---|
| `POST /api/v1/auth/otp/request` | open |
| `POST /api/v1/auth/otp/verify` | open |
| `GET /api/v1/auth/me` | any login |
| `POST /api/v1/auth/logout` | any login |
| `GET /api/v1/users` | `USERS_MANAGE` |
| `POST /api/v1/users` | `USERS_MANAGE` |
| `PUT /api/v1/users/{id}` | `USERS_MANAGE` |
| `GET /api/v1/roles` | `USERS_MANAGE` |
| `GET /api/v1/settings` | any login |
| `PUT /api/v1/settings` | `SETTINGS_EDIT` |

## Rules

1. A user is created with phone and role. Name is optional.
   Example: `{ "phone": "98123 40002", "role": "OFFICE_ADMIN" }` is a complete request.
2. A phone number belongs to one user. A second user with the same phone → 409 `PHONE_ALREADY_USED`.
3. The OTP answer is the same for a known and an unknown phone.
4. OTP limits: 6 digits, 5 minutes, 5 wrong tries, 60 seconds between codes, 5 codes per phone per hour, 20 per IP per hour.
5. The token lasts 30 days. The server checks the user row on every request.
6. Turning a user off, changing the role, changing the phone, or logout → `token_version + 1`.
7. The last active OWNER cannot be turned off or given another role → 409 `LAST_OWNER`.
8. A user cannot turn off their own account → 409 `CANNOT_DISABLE_SELF`.
9. If the table is empty at start and `APP_OWNER_PHONE` is set, create that OWNER.
10. In the `dev` profile the OTP is printed in the console. In `prod` the app refuses to start with only the `log` channel.
11. Every create, update and login writes one `audit_log` row.

## Tasks

- [x] 1.1 `V1__auth_users.sql` with the four tables, checks and indexes from `docs/03-data-model.md`.
- [x] 1.2 `user` package: enums `Role` and `Permission` with the map from `docs/05-roles-permissions.md`. Write `RolePermissionMatrixTest` first.
- [x] 1.3 `AppUser` entity, `AppUserRepository`.
- [ ] 1.4 `audit` package: `AuditLog` entity, `AuditService.record(entityType, entityId, action, summary, details)`.
- [ ] 1.5 `auth` package: `OtpProperties`, `JwtProperties` (`@ConfigurationProperties`). Fail at start if the JWT secret is shorter than 32 bytes.
- [ ] 1.6 `OtpCode` entity and repository. `OtpHasher` (HMAC-SHA256 with `app.otp.hash-secret`).
- [ ] 1.7 `OtpSender` interface, `LogOtpSender`, `OtpDeliveryService` (tries channels in order).
- [ ] 1.8 `OtpService.request(phone, ip)`: normalize, limits, find user, create and send code, same answer always.
- [ ] 1.9 `JwtService`: `issue(AppUser)` and the two beans `JwtEncoder`, `JwtDecoder` (HS256, shared secret).
- [ ] 1.10 `OtpService.verify(phone, code)`: all the checks, mark consumed, return token and user.
- [ ] 1.11 `SecurityConfig` (replaces the temporary one): stateless, open URLs, resource server with our converter, `@EnableMethodSecurity`.
- [ ] 1.12 `UserJwtConverter`: load the user, check `active` and `token_version`, set authorities.
- [ ] 1.13 `CurrentUser` helper and `AuthController` (the four auth URLs).
- [ ] 1.14 `UserService` and `UserController` with rules 1, 2, 6, 7, 8.
- [ ] 1.15 `RolesController` (`GET /roles` returns the matrix).
- [ ] 1.16 `SettingService` and `SettingsController`.
- [ ] 1.17 `OwnerBootstrap` (rule 9).
- [ ] 1.18 A nightly job that deletes `otp_code` rows older than 7 days.
- [ ] 1.19 All tests below are green.

## Tests that must pass

Login:
- `unknownAndKnownPhoneGetTheSameAnswer`
- `onlyKnownPhoneCreatesAnOtpRow`
- `rightCodeGivesAToken`
- `sameCodeCannotBeUsedTwice`
- `codeOlderThanFiveMinutesIsRejected` (fixed `Clock`)
- `fiveWrongTriesLockTheCode`
- `secondRequestWithinSixtySecondsIsRejected`
- `sixthRequestInOneHourIsRejected`
- `otpIsNeverInAnyResponseBody`
- `turnedOffUserCannotRequestOrVerify`

Token:
- `noTokenGives401`
- `tokenOfTurnedOffUserGives401`
- `oldTokenAfterLogoutGives401`
- `roleChangeTakesEffectOnNextRequest`
- `expiredTokenGives401`

Users:
- `ownerCanAddUserWithPhoneAndRoleOnly`
- `officeAdminCannotAddUser` → 403
- `duplicatePhoneGives409`
- `lastOwnerCannotBeTurnedOff`
- `userCannotTurnOffSelf`
- `attendantUserNeedsStaffId` (the check is added now; real staff rows come in Phase 2)

Matrix:
- `RolePermissionMatrixTest` matches the table in `docs/05-roles-permissions.md` cell by cell.

## Done when

- You start the app with `APP_OWNER_PHONE=<your phone>`. You call `otp/request`, read the code in the console, call `otp/verify`, and get a token.
- With that token you add a second user with only a phone and a role.
- The second user can log in and gets 403 on `POST /api/v1/users`.
- You turn the second user off. Their old token gets 401.
- `/check-phase 1` reports all lines as pass.

Try it by hand:

```bash
curl -X POST localhost:8080/api/v1/auth/otp/request -H 'Content-Type: application/json' -d '{"phone":"9812340001"}'
# read the code in the app console, then:
curl -X POST localhost:8080/api/v1/auth/otp/verify -H 'Content-Type: application/json' -d '{"phone":"9812340001","otp":"482913"}'
```

## Out of scope

- Real WhatsApp or SMS sending. That is Phase 5. Only `LogOtpSender` exists now.
- Refresh tokens, passwords, "remember this device".
- A screen to edit which role has which permission.
