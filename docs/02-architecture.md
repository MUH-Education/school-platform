# 2. Architecture

## The big picture

One Spring Boot application (one jar). One PostgreSQL database. Two outside services: an SMS company and a WhatsApp company (they can be the same company).

```
React admin web app ─┐
                     ├─► Spring Boot (JWT on every request) ─► PostgreSQL
Attendant phone app ─┘            │
                                  ├─► SMS provider ──────► parent phone
                                  └─► WhatsApp provider ─► staff phone (OTP)
```

Real-life picture: Spring Boot is the office clerk. Everyone must show an ID card (the JWT). The clerk checks the card, checks the rule book (permissions), then opens the right register (table).

## Packages

Base package: `com.muhjain.school`. One package per feature.

```
com.muhjain.school
├── SchoolApplication.java
├── common/          errors, paging, Clock bean, PhoneNumbers, Indian date helpers
├── auth/            OTP request/verify, JwtService, SecurityConfig, CurrentUser
├── user/            AppUser, Role, Permission, user CRUD
├── audit/           AuditLog, AuditService
├── vehicle/         Vehicle, VehicleDocument
├── staff/           Staff, VehicleAssignment
├── route/           Route, RouteStop, LoadBoardService
├── student/         Student, Guardian, StudentGuardian, TransportEnrolment, photo
├── trip/            BoardingEvent, ManifestService, BusStatusService, AlertService
├── messaging/       MessageOutbox, templates, SmsSender, WhatsAppSender, OutboxWorker
├── enquiry/         Enquiry, EnquiryFollowUp
├── fee/             AcademicSession, ClassFee, FeePlan, FeeDue, FeePayment
└── analytics/       read-only queries for graphs
```

## Layers inside a feature

Example for `vehicle`:

| Class | Job | Example |
|---|---|---|
| `VehicleController` | Reads the HTTP request, checks permission, returns a response record | `POST /api/v1/vehicles` |
| `VehicleService` | Business rules and the transaction | "Registration number must be unique" |
| `VehicleRepository` | Database access (Spring Data JPA) | `findByActiveTrueOrderByName()` |
| `Vehicle` | JPA entity = one table row | `seats = 14` |
| `VehicleRequest`, `VehicleResponse` | Java records for JSON in and out | `{ "name": "Van 4", "seats": 14 }` |

Rules:

- Controller → Service → Repository. Never skip a layer.
- A feature calls another feature only through its service. Example: `TripService` asks `StudentService.findOnRoute(routeId, date)`. It does not touch `StudentRepository`.
- Entities hold ids of other features, not objects. Example: `Route` has `Long vehicleId`, not `Vehicle vehicle`. This keeps features separate and avoids lazy-loading errors.

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
