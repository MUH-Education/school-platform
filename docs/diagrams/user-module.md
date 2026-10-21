# User module — flow diagrams

Everything about users and login, in pictures. Open this file in GitHub or IntelliJ (Markdown preview) to see
the diagrams drawn.

Code lives in `com.muhjain.school`. Rules come from `docs/04-login-otp-jwt.md`, `docs/05-roles-permissions.md`
and `docs/phases/phase-1-login-users.md`.

| # | Diagram | Answers |
|---|---|---|
| 1 | Files and layers | Which class calls which |
| 2 | Tables | What is stored where (Postgres and Redis) |
| 3 | Ask for a code | `POST /auth/otp/request` |
| 4 | Send the code | WhatsApp → SMS → log, and what is still missing |
| 5 | Check the code, log in | `POST /auth/otp/verify` |
| 6 | Every request after login | How the token becomes "user 2, OWNER, these permissions" |
| 7 | Add or change a user | `POST /users`, `PUT /users/{id}` and their rules |
| 8 | Log out and end sessions | One device, or every device |
| 9 | Roles and permissions | Who may do what |

---

## 1. Files and layers

Arrows mean "calls". A controller never calls a repository. Other features call `UserService`, never
`AppUserRepository`.

```mermaid
flowchart LR
    subgraph C["controller"]
        AC["AuthController<br/>/api/v1/auth/*"]
        UC["UserController<br/>/api/v1/users"]
        RC["RolesController<br/>/api/v1/roles"]
    end

    subgraph SEC["security"]
        SCFG["SecurityConfig<br/>open URLs, 401/403 bodies"]
        JWTC["UserJwtConverter<br/>token → session → permissions"]
        CU["CurrentUser<br/>id, role, staffId, sessionId"]
        RQC["RequestContext<br/>ThreadLocal, cleared after request"]
        JS["JwtService<br/>signs HS256 token"]
    end

    subgraph SA["service.auth"]
        OS["OtpService<br/>request, verify, limits"]
        ROS["RedisOtpStore<br/>otp:code:*, otp:window:*"]
        ODS["OtpDeliveryService<br/>tries channels in order"]
        OSND["OtpSender (interface)"]
        SS["SessionService<br/>create, resolve, revoke, revokeAll"]
    end

    subgraph SAI["service.auth.impl"]
        WA["WhatsAppOtpSender"]
        SMS["SmsOtpSender"]
        LOG["LogOtpSender<br/>local only"]
    end

    subgraph SU["service.user"]
        US["UserService<br/>list, create, update, rules"]
        OB["OwnerBootstrap<br/>first owner at start"]
    end

    subgraph OTHER["other features"]
        AUD["AuditService"]
        STF["StaffService"]
        ARS["AttendantRouteService"]
        PAPI["ProviderApi (interface)<br/>❌ no real class yet"]
    end

    subgraph R["model.repository"]
        AUR["AppUserRepository"]
        USR["UserSessionRepository"]
        OWR["OtpWhitelistRepository"]
    end

    AC --> OS
    AC --> US
    AC --> SS
    AC --> ARS
    UC --> US
    RC --> US

    JWTC --> SS
    JWTC --> US
    JWTC --> RQC
    CU --> RQC

    OS --> ROS
    OS --> ODS
    OS --> US
    OS --> SS
    OS --> AUD
    OS --> OWR
    ODS --> OSND
    OSND -.-> WA
    OSND -.-> SMS
    OSND -.-> LOG
    WA --> PAPI
    SMS --> PAPI
    SS --> USR
    SS --> JS

    US --> AUR
    US --> AUD
    US --> STF
    US --> SS
    OB --> US
```

---

## 2. Tables

Postgres keeps what must last. Redis keeps short-lived things: OTP codes and the fast copy of each session.

```mermaid
erDiagram
    app_user {
        bigint id PK
        varchar phone "unique, +91XXXXXXXXXX"
        varchar name
        varchar role "OWNER, OFFICE_ADMIN, TRANSPORT_INCHARGE, ADMISSIONS_DESK, ATTENDANT"
        bigint staff_id FK "only ATTENDANT, unique"
        boolean active "never deleted, only turned off"
        timestamptz last_login_at
        varchar last_login_client "WEB or ATTENDANT_APP"
        bigint created_by FK
    }
    user_session {
        bigint id PK
        bigint user_id FK
        varchar session_hash "HMAC of session id, never plain"
        timestamptz revoked_at "set on logout"
        varchar ip_address
        timestamptz created_at "12 h hard cap counts from here"
    }
    otp_whitelist {
        varchar phone PK "test phones, code always 123456"
        varchar note
    }
    audit_log {
        bigint id PK
        varchar entity_type "USER"
        bigint entity_id
        varchar action "CREATED, UPDATED (also logout), LOGIN"
        varchar summary
        jsonb details "old and new values"
        bigint changed_by FK
        timestamptz changed_at
    }
    staff {
        bigint id PK
        varchar staff_type "ATTENDANT, DRIVER, HELPER, TEACHER"
    }

    staff ||--o| app_user : "staff_id (attendant)"
    app_user ||--o{ user_session : "one row per login"
    app_user ||--o{ audit_log : "changed_by"
    app_user ||--o{ app_user : "created_by"
```

Redis keys (nothing here needs to survive a restart):

| Key | Holds | Lives |
|---|---|---|
| `otp:code:<phone>` | HMAC of the code, channel, wrong tries | 5 minutes |
| `otp:window:phone:<phone>`, `otp:window:ip:<ip>` | times of recent codes, for the limits | 1 hour |
| `session:<id>` | user id, role, staffId, idle deadline | 15 minutes idle, slides on every request |
| `user_sessions:<userId>` | the set of that user's session ids | used by "log out everywhere" |

---

## 3. Ask for a code — `POST /api/v1/auth/otp/request`

The answer is the same for a known and an unknown phone, so nobody can find out who is a user.

```mermaid
flowchart TD
    A["Phone app / web sends<br/>{ phone: 98123 40002 }"] --> B["PhoneNumbers.normalize<br/>→ +919812340002"]
    B -->|bad number| E400["400 VALIDATION"]
    B --> L{"Limits OK?<br/>60 s since last code<br/>5 per phone per hour<br/>20 per IP per hour"}
    L -->|no| E429["429 OTP_TOO_MANY_REQUESTS<br/>+ Retry-After header"]
    L -->|yes| U{"Active user<br/>with this phone?"}
    U -->|no| SAME["Send nothing"]
    U -->|yes| W{"Phone in<br/>otp_whitelist?"}
    W -->|yes| FIX["Code = 123456<br/>no message sent"]
    W -->|no| GEN["SecureRandom 6-digit code"]
    GEN --> SEND{"OtpDeliveryService.send<br/>(see diagram 4)"}
    FIX --> STORE["Redis otp:code:phone = HMAC of code, 5 min<br/>+ count it in otp:window:phone:* and otp:window:ip:*"]
    SEND -->|a channel worked| STORE
    SEND -->|every channel failed| ERR["log.error with masked phone<br/>nothing saved"]
    ERR --> OK
    STORE --> OK
    SAME --> OK["200 { message: If this number is registered,<br/>a code has been sent. expiresInSeconds: 300,<br/>resendAfterSeconds: 60 }"]
```

---

## 4. Send the code — WhatsApp, then SMS, then log

`app.otp.channels` decides the order. The first channel that works wins.

```mermaid
flowchart TD
    S["OtpDeliveryService.send(phone, code)"] --> CH{"app.otp.channels"}
    CH -->|"prod: whatsapp,sms"| WA["WhatsAppOtpSender"]
    CH -->|"local: log"| LG["LogOtpSender<br/>prints: OTP for +91XXXXXX0000 is 509927"]

    WA --> P1["ProviderApi.sendWhatsAppOtp"]
    P1 -->|works| DONE["return WHATSAPP"]
    P1 -->|fails or no class| SM["SmsOtpSender"]
    SM --> P2["ProviderApi.sendSmsOtp<br/>DLT OTP template"]
    P2 -->|works| DONE2["return SMS"]
    P2 -->|fails| FAIL["OtpSendException<br/>every channel failed"]

    MISSING["❌ Missing today: one @Component that implements ProviderApi<br/>(question C1: which SMS/WhatsApp company).<br/>So prod refuses to start (ProdMessagingCheck)."]
    P1 -.- MISSING
    P2 -.- MISSING
```

---

## 5. Check the code and log in — `POST /api/v1/auth/otp/verify`

Wrong code, old code, no code, unknown user, turned-off user: all give the same `401 OTP_INVALID`.

```mermaid
sequenceDiagram
    autonumber
    participant App as Phone app / Web
    participant AC as AuthController
    participant OS as OtpService
    participant R as Redis
    participant US as UserService
    participant AU as AuditService
    participant SS as SessionService
    participant DB as Postgres

    App->>AC: { phone: "9812340002", otp: "509927" }
    AC->>OS: verify(phone, otp, ip)
    OS->>R: GET otp:code:+919812340002
    OS->>US: findActiveByPhone
    alt no code, old code, unknown or turned-off user
        OS-->>App: 401 OTP_INVALID
    else 3 wrong tries already
        OS-->>App: 429 OTP_LOCKED (ask for a new code)
    else wrong code
        OS->>R: wrong tries + 1
        OS-->>App: 401 OTP_INVALID
    else HMAC matches (MessageDigest.isEqual)
        OS->>R: DELETE the code (one use only)
        OS->>US: recordLogin(userId, now)
        US->>DB: UPDATE app_user SET last_login_at
        OS->>AU: record USER, LOGIN, "Logged in by OTP (WHATSAPP)"
        OS->>SS: create(user, ip)
        SS->>DB: INSERT user_session (session_hash)
        SS->>R: SET session:<id> = userId, role, staffId (15 min)
        SS-->>OS: JWT { sub: userId, sid: sessionId, exp: +12 h }
        OS-->>App: 200 { token, expiresAt, user: { id, phone, role, permissions } }
    end
```

---

## 6. Every request after login

The role comes from the session, not from the token. So a role change works at once.

```mermaid
flowchart TD
    REQ["GET /api/v1/vehicles<br/>Authorization: Bearer token"] --> T{"Token signed by us<br/>and exp not passed?"}
    T -->|no / no token| U401["401 UNAUTHENTICATED"]
    T -->|yes| SID["Read sid from token"]
    SID --> RD{"Redis session:sid"}
    RD -->|found and fresh| SLIDE["Push idle deadline 15 min forward<br/>(never past 12 h from login)"]
    RD -->|found but idle > 15 min<br/>or past 12 h| U401
    RD -->|not found<br/>Redis restarted| PG{"Postgres user_session:<br/>not revoked and < 12 h?<br/>user still active?"}
    PG -->|no| U401
    PG -->|yes| REFILL["Put it back in Redis"]
    REFILL --> AUTH
    SLIDE --> AUTH["Authorities = ROLE_OWNER<br/>+ every permission of the role"]
    AUTH --> CTX["RequestContext = userId, role, staffId, sid"]
    CTX --> PRE{"@PreAuthorize<br/>hasAuthority('VEHICLES_VIEW')"}
    PRE -->|no| F403["403 FORBIDDEN"]
    PRE -->|yes| CTRL["Controller → Service → Repository"]
    CTRL --> CLEAN["RequestContextCleanupFilter<br/>clears ThreadLocal"]
```

---

## 7. Add or change a user — `POST /users`, `PUT /users/{id}`

Only `USERS_MANAGE` (the owner). Every change is saved in `audit_log`.

```mermaid
flowchart TD
    IN["POST /users { phone, role, name, staffId }<br/>PUT /users/{id} { phone, role, name, staffId, active }"] --> PM{"hasAuthority<br/>USERS_MANAGE?"}
    PM -->|no| F403["403 FORBIDDEN"]
    PM -->|yes| NORM["PhoneNumbers.normalize(phone)"]
    NORM --> STAFF{"staffId rule"}
    STAFF -->|"ATTENDANT without staffId"| V1["400 staffId is required for an attendant"]
    STAFF -->|"other role with staffId"| V2["400 staffId is only for an attendant"]
    STAFF -->|"staff row missing / not ATTENDANT type"| V3["400 staffId does not exist / wrong type"]
    STAFF -->|ok| PH{"Phone used by<br/>another user?"}
    PH -->|yes| C409["409 PHONE_ALREADY_USED"]
    PH -->|no| KIND{"POST or PUT?"}

    KIND -->|POST| SAVE1["INSERT app_user, created_by = current user"]
    SAVE1 --> A1["audit CREATED: User +91... added as OFFICE_ADMIN"]
    A1 --> R201["201 UserResponse"]

    KIND -->|PUT| NF{"User exists?"}
    NF -->|no| E404["404 NOT_FOUND"]
    NF -->|yes| SELF{"Turning off yourself?"}
    SELF -->|yes| CS["409 CANNOT_DISABLE_SELF"]
    SELF -->|no| LO{"Last active OWNER<br/>loses OWNER or active?<br/>(row lock)"}
    LO -->|yes| LAST["409 LAST_OWNER"]
    LO -->|no| DIFF{"Anything changed?"}
    DIFF -->|no| R200a["200, nothing saved"]
    DIFF -->|yes| SAVE2["UPDATE app_user"]
    SAVE2 --> END{"phone or role changed,<br/>or turned off?"}
    END -->|yes| REV["SessionService.revokeAll(userId)<br/>every device logged out"]
    END -->|no| A2
    REV --> A2["audit UPDATED: Role changed from X to Y. Turned off."]
    A2 --> R200["200 UserResponse"]
```

First owner: when `app_user` is empty at start, `OwnerBootstrap` creates an OWNER with `APP_OWNER_PHONE`
(local default `9999900000`).

---

## 8. Log out and end sessions

```mermaid
flowchart LR
    LOGOUT["POST /auth/logout<br/>(any login)"] --> ONE["SessionService.revoke(sid)<br/>DELETE Redis session:sid<br/>user_session.revoked_at = now<br/>audit: Logged out (this device)"]
    ONE --> R204["204 — only this device.<br/>Other phones keep working."]

    CHANGE["Owner changes phone or role,<br/>or turns the user off"] --> ALL["SessionService.revokeAll(userId)<br/>every row revoked + every Redis entry deleted"]
    ALL --> NEXT["Next request from any device<br/>→ 401, must log in again"]

    IDLE["No request for 15 min,<br/>or 12 h since login"] --> GONE["Redis entry expires<br/>→ 401"]
```

---

## 9. Roles and permissions

Roles are fixed in `model/enums/Role.java`, not in a table. Controllers check **permissions**, never roles.

```mermaid
flowchart LR
    OWNER["OWNER"] --> ALLP["all 24 permissions<br/>incl. USERS_MANAGE, SETTINGS_EDIT,<br/>STAFF_SALARY_*, MARKETING_*, FEES_CORRECT"]

    OA["OFFICE_ADMIN"] --> OAP["BUS_STATUS_VIEW, TRIPS_RECORD_ANY,<br/>ROUTES_VIEW, VEHICLES_VIEW,<br/>STUDENTS_VIEW/EDIT, ADMISSIONS_CREATE,<br/>MESSAGES_VIEW, ENQUIRIES_VIEW/EDIT,<br/>FEES_VIEW/EDIT, ANALYTICS_VIEW"]

    TI["TRANSPORT_INCHARGE"] --> TIP["BUS_STATUS_VIEW, TRIPS_RECORD,<br/>TRIPS_RECORD_ANY, ROUTES_VIEW/EDIT,<br/>VEHICLES_VIEW/EDIT, STUDENTS_VIEW,<br/>MESSAGES_VIEW"]

    AD["ADMISSIONS_DESK"] --> ADP["STUDENTS_VIEW, ADMISSIONS_CREATE,<br/>ENQUIRIES_VIEW/EDIT, FEES_VIEW/EDIT,<br/>ANALYTICS_VIEW"]

    AT["ATTENDANT"] --> ATP["TRIPS_RECORD only<br/>+ only own route today<br/>(staff_id → vehicle → route)"]
```

Test logins in `local` (code always `123456`): `9999900001` OWNER, `9999900002` OFFICE_ADMIN,
`9999900003` TRANSPORT_INCHARGE, `9999900004` ATTENDANT.
