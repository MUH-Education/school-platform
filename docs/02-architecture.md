# 2. Architecture

## The big picture

One Spring Boot application (one jar). One PostgreSQL database. One Redis (OTP codes and login sessions only — docs/08-decisions.md). Two outside services: an SMS company and a WhatsApp company (they can be the same company).

```
React admin web app ─┐
                     ├─► Spring Boot (JWT on every request) ─► PostgreSQL
Attendant phone app ─┘            │         │
                                  │         └─► Redis (OTP codes, login sessions)
                                  ├─► SMS provider ──────► parent phone
                                  └─► WhatsApp provider ─► staff phone (OTP)
```

Real-life picture: Spring Boot is the office clerk. Everyone must show an ID card (the JWT). The clerk checks the card, checks the rule book (permissions), then opens the right register (table).

## Packages

Base package: `com.muhjain.school`. One package per **layer**, not per feature. Every class for every feature (vehicle, staff, route, student, trip, ...) lives in the layer package that matches its job.

```
com.muhjain.school
├── SchoolApplication.java
├── controller/           every XController (one per feature)
├── service/
│   ├── auth/              OtpService, RedisOtpStore, SessionService, OtpDeliveryService
│   │   └── impl/           OtpSender implementations: LogOtpSender, SmsOtpSender, WhatsAppOtpSender
│   ├── student/, fee/, trip/, route/, staff/, vehicle/, messaging/, enquiry/, user/, ...
│   │   └── impl/           each feature's own interface implementations, where it has any
│   └── analytics/, audit/, setting/, dev/   one sub-package per remaining feature
├── model/
│   ├── entity/            every @Entity (Vehicle, Staff, Route, Student, UserSession, ...)
│   ├── repository/        every Spring Data repository (VehicleRepository, StaffRepository, UserSessionRepository, ...)
│   ├── enums/             plain enums used by entities or requests (Gender, PayFrequency, Role, ...)
│   └── dto/
│       ├── request/        every ...Request record
│       └── response/       every ...Response record, and any other DTO-shaped record (StudentFilter, Crew, ...)
├── config/               @Configuration and @ConfigurationProperties classes
├── security/             SecurityConfig, JwtService, JwtProperties, SessionProperties, CurrentUser,
│                         UserJwtConverter, RequestContext, RequestContextData, RequestContextCleanupFilter, TripAccess
├── exception/            ApiException, ApiErrorResponse, GlobalExceptionHandler, and other XException classes
└── util/                 small static helper classes with no state (PhoneNumbers, MoneyText, calculators)
```

A class keeps the feature name as a prefix when the plain name alone would collide (example: `AnalyticsStudentFilter` in `model.dto.response`, next to `StudentFilter` which is the Students-list filter).

An interface with more than one implementation (example: `OtpSender`, implemented by `LogOtpSender`, `SmsOtpSender`, `WhatsAppOtpSender`) sits at the top of its feature's `service` sub-package; its implementations sit one level down, in `impl`. A couple of interfaces are implemented outside their own feature — `StudentCounts` (interface, in `service.route`) is implemented by `EnrolmentStudentCounts` (in `service.route.impl`, even though the class itself is about students), and `BoardingNotifier` (interface, in `service.trip`) is implemented by `OutboxBoardingNotifier` (in `service.trip.impl`, even though it is messaging code). The interface's home decides the package, so the contract and its implementations are always easy to find together.

## Layers

Example for vehicles:

| Class | Package | Job | Example |
|---|---|---|---|
| `VehicleController` | `controller` | Reads the HTTP request, checks permission, returns a response record | `POST /api/v1/vehicles` |
| `VehicleService` | `service.vehicle` | Business rules and the transaction | "Registration number must be unique" |
| `VehicleRepository` | `model.repository` | Database access (Spring Data JPA) | `findByActiveTrueOrderByName()` |
| `Vehicle` | `model.entity` | JPA entity = one table row | `seats = 14` |
| `VehicleRequest` | `model.dto.request` | Java record for JSON in | `{ "name": "Van 4", "seats": 14 }` |
| `VehicleResponse` | `model.dto.response` | Java record for JSON out | `{ "id": 4, "name": "Van 4", "seats": 14 }` |

Rules:

- Controller → Service → Repository. Never skip a layer.
- A controller or service for one feature may call the service of another feature, never its repository directly. Example: `TripService` asks `StudentService.findOnRoute(routeId, date)`. It does not touch `StudentRepository`.
- Entities hold ids of other features, not objects. Example: `Route` has `Long vehicleId`, not `Vehicle vehicle`. This keeps features separate and avoids lazy-loading errors.
- A member (method, field, nested type) used by a class outside its own file must be `public`. Package-private only works when both classes sit in the same layer package by accident; do not rely on that — write `public` for anything meant to be used elsewhere.

## Request life cycle

1. Request arrives with header `Authorization: Bearer <jwt>`.
2. Spring Security checks the JWT signature and expiry.
3. Our converter loads the user row by id. If the user is turned off, or the token version is old, answer 401.
4. The converter gives the user their permissions (from the role).
5. `@PreAuthorize` on the controller method checks the permission. If missing, answer 403.
6. The service runs inside a transaction.
7. The controller returns a response record. Jackson turns it into JSON.

## Errors

One class `ApiException(HttpStatus status, String code, String message)`. One `@RestControllerAdvice` turns every error into:

```json
{ "error": "VEHICLE_IN_USE", "message": "This vehicle runs Route 4. Move the route first.", "fields": null }
```

| HTTP | When | Example code |
|---|---|---|
| 400 | Input is wrong | `VALIDATION` with `fields: {"seats": "must be greater than 0"}` |
| 401 | No token, bad token, user turned off | `UNAUTHENTICATED` |
| 403 | Logged in but not allowed | `FORBIDDEN`, `NOT_YOUR_ROUTE` |
| 404 | Row does not exist | `NOT_FOUND` |
| 409 | Breaks a business rule | `PHONE_ALREADY_USED`, `VEHICLE_IN_USE` |
| 429 | Too many tries | `OTP_TOO_MANY_REQUESTS` |

## Lists

Lists that can grow (students, enquiries, messages, audit) use paging: `?page=0&size=25&sort=name,asc`. Response:

```json
{ "items": [ ... ], "page": 0, "size": 25, "totalItems": 290, "totalPages": 12 }
```

Small lists (vehicles, routes, staff, users) return the full array.

## Configuration

`application.yml` holds safe defaults. Profiles:

| Profile | Used for | Database | Messages |
|---|---|---|---|
| `dev` (default) | Your laptop | PostgreSQL from `compose.yaml` | `log` sender: prints to console |
| `test` | `./gradlew test` | Testcontainers PostgreSQL | mock or `log` sender |
| `prod` | The server | Managed PostgreSQL, from env vars | real provider |

Our own settings live under `app.*`:

```yaml
app:
  zone: Asia/Kolkata
  jwt:
    secret: ${APP_JWT_SECRET}      # at least 32 bytes
    ttl: 30d
  otp:
    length: 6
    ttl: 5m
    max-attempts: 5
    resend-after: 60s
    max-per-hour: 5
    channels: whatsapp,sms         # try in this order; "log" in dev
    hash-secret: ${APP_OTP_SECRET}
  bootstrap:
    owner-phone: ${APP_OWNER_PHONE:}   # first OWNER, created only if no user exists
  messaging:
    sms-provider: log              # log | msg91 | ...
    whatsapp-provider: log
```

Bind these with one `@ConfigurationProperties` record per group (`JwtProperties`, `OtpProperties`).

## Time

- One `Clock` bean: `Clock.system(ZoneId.of("Asia/Kolkata"))`. Everything that needs "now" injects it.
- `service_date` (the school day of a trip) is a `LocalDate` in the school zone.
- All `timestamptz` values are instants. The API sends them as ISO text with offset, for example `2026-10-07T07:42:10+05:30`.

## Files (student photos)

One interface `PhotoStorage` with `save(bytes) → key` and `open(key) → stream`.

- `dev`: `LocalFolderPhotoStorage` writes under `./data/photos`.
- `prod`: `S3PhotoStorage` (an S3-compatible bucket in India).

The database stores only the key. The photo is served by `GET /api/v1/students/{id}/photo`, which checks permission.

## Build file

Create the project on start.spring.io so the starter names are right for the current Spring Boot 4 version. `docs/phases/phase-0-setup.md` lists the exact choices.
