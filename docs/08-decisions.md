# 8. Decisions and open questions

This file is the memory of the project. When something is decided, write one line here with the date.

## A. Decided by the owner

| # | Decision | Date |
|---|---|---|
| A1 | Backend first. Spring Boot 4, Java 25, Gradle. | 7 Oct 2026 |
| A2 | PostgreSQL for every entity. | 7 Oct 2026 |
| A3 | A user is registered with phone number and role only. | 7 Oct 2026 |
| A4 | Login by OTP, sent on WhatsApp or by SMS. | 7 Oct 2026 |
| A5 | Simple JWT for sessions. | 7 Oct 2026 |
| A6 | No parent app. Parents get SMS only. | 7 Oct 2026 |
| A7 | One admin web app holds bus status, routes, vehicles and staff, students, admissions with fees, enquiries, analytics, users and roles. | 7 Oct 2026 |
| A8 | Bus attendants use a phone app in Hindi. | 7 Oct 2026 |

## B. Decided while planning (change any of these if you disagree)

Each one has the reason and what it would cost to change later.

| # | Decision | Why | Cost to change later |
|---|---|---|---|
| B1 | One JWT, valid 30 days. No refresh token. The server checks the user row on every request. | Simple. Few OTPs, so low cost. Turning a user off still works at once. | Low |
| B2 | Roles and permissions are fixed in Java code, 5 roles. | "Phone and role only" needs no role editor. Less code, fewer mistakes. | Medium: needs tables and a screen |
| B3 | OTP: 6 digits, 5 minutes, 5 wrong tries, new code after 60 seconds, 5 per hour. | Normal, safe values. | Low: settings |
| B4 | OTP tries WhatsApp first, then SMS. | Cheaper, and staff read WhatsApp. | Low: one setting |
| B5 | `name` on a user is optional, not forbidden. | Screens show "changed by Neelam". | Low |
| B6 | One Spring Boot app with one package per feature. No microservices. | 290 students. One developer. | High, but not needed |
| B7 | Flyway SQL files own the schema. Hibernate only checks (`validate`). | The database is never changed by surprise. | — |
| B8 | Tests use a real PostgreSQL (Testcontainers). Docker is needed on the developer's machine. | Tests then prove what production will do. | Low |
| B9 | No Lombok, no MapStruct. Records for API classes. | Fewer tools that can break on a new Java version. | Low |
| B10 | Vehicles, staff, users, routes and students are turned off, never deleted. | Old trips and SMS still point at them. | — |
| B11 | Bus position comes from attendant taps. No GPS. | No device cost. Works now. | Medium: new module |
| B12 | Parent SMS goes through a queue table. OTP is sent directly. | SMS must not be lost. Login must not wait. | — |
| B13 | Hindi SMS has a boy text and a girl text (8 templates). | Natural Hindi. Parents read this 4 times a day. | Low, but 8 DLT approvals instead of 4 |
| B14 | The office can correct a tap from the web (`TRIPS_RECORD_ANY`). | Attendants make mistakes. | Low |
| B15 | Fees are recorded in this system: plan, dues, payments. Status is calculated. | The admission and analytics screens need it. | See question C2 |
| B16 | Analytics download is a CSV file (opens in Excel). | No extra library. | Low |
| B17 | Student photos sit behind a permission check. Local folder in dev, S3-style bucket in production. | A child's photo must never be public. | Low |
| B18 | All URLs start with `/api/v1`. | Room for a v2 without breaking phones. | — |
| B19 | Student `class_name` is one current value. Promotion to the next class each April is a later task. | Keeps Phase 3 small. | Medium |
| B20 | In the evening, the attendant must answer for every child before the trip can start. The server reports missing children on Bus status. | Child safety. | Low |
| B21 | A wrong payment is fixed by a correction row, by the owner only. Payments are never edited. | Money records must keep their history. | — |

## C. Open questions for the owner

Nothing in Phase 0 to 4 is blocked by these. Each one says which phase needs the answer.

| # | Question | Needed by | What happens if not answered |
|---|---|---|---|
| C1 | Which company for SMS and WhatsApp? Who starts DLT registration? | Phase 5 | Real messages cannot go out. Everything else works in test mode. |
| C2 | Does the accountant already keep fees in other software? If yes, who types payments here? | Phase 7 | Risk of typing every payment twice. |
| C3 | Is it fine that Analytics shows a child's name next to fee status and father's occupation? | Phase 8 | Default: yes, but only OWNER, OFFICE_ADMIN and ADMISSIONS_DESK can open it. |
| C4 | What does "student average graph" mean: marks, attendance, or fee per student? | Phase 8 | Default: students in each class. |
| C5 | Bus fee when a child starts mid-year: typed by the office, or calculated (fee ÷ months × months left)? | Phase 7 | Default: typed by the office. |
| C6 | Staff types: are DRIVER, ATTENDANT and HELPER enough? | Phase 2 | Default: these three. |
| C7 | After how many days late is a fee "delayed", and when "defaulted"? | Phase 7 | Default: 10 days and 60 days. |
| C8 | Where will it run (which server company)? Data must stay in India. | Phase 9 | — |
| C9 | Should a parent get an SMS when the child is marked absent? | Phase 5 | Default: no. A wrong tap would scare a parent. |
| C10 | Should the attendant's phone show parents' phone numbers? | Phase 4 | Default: no. The office calls the parent. |

## D. Decisions made while building

Add a line each time code needed a choice the docs did not cover.

| Date | Phase | Decision | Why |
|---|---|---|---|
| 6 Oct 2026 | 0 | start.spring.io was blocked in the build environment, so the Gradle files were written by hand: Spring Boot 4.1.1, Gradle 9.8.0, same starters as start.spring.io. | Newest stable versions on that day. |
| 6 Oct 2026 | 0 | An error the docs do not name uses the HTTP status name as its code. Example: a crash → 500 `INTERNAL_SERVER_ERROR`, PUT on a GET-only URL → 405 `METHOD_NOT_ALLOWED`. The real error goes only to the log. | Owner's answer. One simple rule, the React app can still switch on `error`. |
| 6 Oct 2026 | 0 | Keep Spring Boot's health answer `{"groups":["liveness","readiness"],"status":"UP"}`. It counts as `{"status":"UP"}` for Phase 0. | Owner's answer. `/actuator/health/liveness` and `/readiness` help the server in Phase 9. |
| 6 Oct 2026 | 0 | The root `README.md` (a copy of `docs/phases/README.md`) is replaced by the short "how to run, how to test" README. | Owner's answer. The phase list still lives in `docs/phases/README.md`. |
| 6 Oct 2026 | 1 | Java 25 comes from the cloud environment's setup script (option A). The build file is not changed. In the Phase 1 session it was installed by hand with `apt-get install openjdk-25-jdk-headless`. | Owner's answer. |
| 6 Oct 2026 | 1 | `.claude/rules/security.md` was missing. Claude wrote it from the security rules in `CLAUDE.md`, `docs/04` and `docs/05`. No new rules. | Owner said "do what you want". |
| 6 Oct 2026 | 1 | `dev` and `test` get fixed "dev only" values for `APP_JWT_SECRET` and `APP_OTP_SECRET` in `application.yml` (the env var still wins). `prod` has no default, so the app does not start without them. | Owner said "do what you want". `./gradlew bootRun` and `./gradlew test` work with no setup. |
| 6 Oct 2026 | 1 | `APP_OTP_SECRET` also needs at least 32 bytes, like the JWT secret. | Same strength for both keys. One rule to remember. |
| 6 Oct 2026 | 1 | The "20 codes per IP per hour" limit is the setting `app.otp.max-per-ip-per-hour: 20`. | The doc gave the number but no setting name. |
| 6 Oct 2026 | 1 | `retryAfterSeconds` of a 429 is sent as the standard `Retry-After` header. Example: `Retry-After: 42`. The error body keeps its 3 fields. | One error shape for the React app. |
| 6 Oct 2026 | 1 | If every OTP channel fails, the answer is still the same 200, no `otp_code` row is saved, and the error is logged with a masked phone. | A different answer would tell a stranger the number is registered. |
| 6 Oct 2026 | 1 | Known limit: the per-phone and per-IP limits count only `otp_code` rows, and an unknown phone makes no row. So an unknown phone never gets 429. | The doc says "count from otp_code rows, no extra table". An unknown phone costs nothing, because no message is sent. |
| 6 Oct 2026 | 1 | `created_at` and `updated_at` are filled by Spring Data JPA auditing with the app `Clock`, cut to microseconds like PostgreSQL. | Tests with a fixed clock get fixed times. No `Instant.now()` without the clock. |
| 6 Oct 2026 | 1 | `staffId` is required for ATTENDANT and refused for other roles (400 `VALIDATION`). The database also checks it. Phase 2 adds the foreign key and checks the staff type. | Phase 1 rule "Only for ATTENDANT". |
| 6 Oct 2026 | 1 | `PUT /users/{id}` gets the whole user (`phone`, `role`, `active` required; `name`, `staffId` optional). Example: `{"phone":"9812340002","role":"OFFICE_ADMIN","active":false}`. | Matches an edit form. No "missing means unchanged" guessing. |
| 6 Oct 2026 | 1 | Logout writes an `audit_log` row (UPDATED, "Logged out on all devices"). | It changes `token_version` on the user row. |
| 6 Oct 2026 | 1 | Settings live in a new package `com.muhjain.school.setting`. | Not in the CLAUDE.md feature list. It is not part of user or common. |
| 6 Oct 2026 | 1 | `PUT /settings` body: `{"values": {"transport.bus_fee_per_year": "9000"}}`. Only existing keys. `school.name` is text up to 300 characters. Every other key is a whole number 0 or more. `transport.collection_pct` is at most 100. One bad value → 400 and nothing is saved. | The doc did not give the body or value rules. |
| 6 Oct 2026 | 1 | A settings change is audited as entity_type `SETTING`, entity_id `0`, with the key in `details`. | `audit_log.entity_id` is a number, and a setting's id is its text key. |
| 6 Oct 2026 | 1 | The OTP clean-up job runs every night at 02:30 school time. | The doc said "nightly" only. |
| 6 Oct 2026 | 1 | `prod` cannot start in Phase 1: its channels are `whatsapp,sms`, and those senders come in Phase 5. The app stops with a clear message. | A missing channel must stop the app, not silently send nothing. |
| 7 Oct 2026 | 2 | The Phase 1 tests used a made-up `staffId` 14. The new foreign key `app_user.staff_id → staff` rejects it. So in task 2.1 the test helper `addUser` makes a real staff row for an ATTENDANT, and `UserManagementTest` uses real ids. The service rule "must be a real ATTENDANT staff row" is still task 2.14. | The build must be green after every task. |
| 7 Oct 2026 | 2 | Vehicle name and registration number are unique ignoring capital letters and spaces. The database has a unique index on `upper(replace(col, ' ', ''))`. The text is saved as typed, with white space cleaned to single spaces ("Van 4" stays "Van 4"). Route names follow the same rule. | Doc rule 1. For routes it was my choice, "Route 4" and "route4" are the same mistake. |
| 7 Oct 2026 | 2 | New 409 codes: `VEHICLE_NAME_ALREADY_USED`, `REGISTRATION_ALREADY_USED`, `ROUTE_NAME_ALREADY_USED`, `STAFF_INACTIVE`, `VEHICLE_INACTIVE`, `STAFF_TYPE_IN_USE`, `FROM_DATE_TOO_EARLY`, `TEMPORARY_OVERLAP`. | The docs name only some codes. Same style as `PHONE_ALREADY_USED`. |
| 7 Oct 2026 | 2 | `PUT /vehicles/{id}` and `PUT /staff/{id}` take the whole object with `active`, like Phase 1 `PUT /users/{id}`. `active: true` turns it on again. `DELETE` is the short way to turn off (204). | B10 says never delete. Without this there is no way back after a wrong turn-off. |
| 7 Oct 2026 | 2 | `PUT /vehicles/{id}/documents` body is four dates: `{"fitness": ..., "insurance": ..., "permit": ..., "puc": ...}`. A date that is null or missing removes that paper. The answer is the whole vehicle. `daysLeft` is below 0 when ended. `papersStatus` is the worst of the four, in the order ENDED, ENDING_SOON, MISSING, VALID. | The docs say "save the four paper dates" but give no body. |
| 7 Oct 2026 | 2 | A vehicle response always lists all four papers, in the order FITNESS, INSURANCE, PERMIT, PUC. A paper with no row is MISSING. | The React screen can show four boxes without guessing. |
| 7 Oct 2026 | 2 | `VehicleAssignment` and its URL live in the `staff` package, as in docs/02-architecture.md. `AssignmentController` maps `/api/v1/vehicles/{id}/assignments`. | Keeps the assignment rules next to the assignment code. |
| 7 Oct 2026 | 2 | Rule 10 (STAFF_BUSY) counts any row of the person that touches the new days, on any vehicle, also the same vehicle and a row that a temporary replacement covers. Example: Rajpal drives Van 1. He cannot also be temporary driver of Van 2 while his own Van 1 row exists. | Simple and safe. A smarter check ("he is on leave, so he is free") can come later. |
| 7 Oct 2026 | 2 | A permanent change must start after the day the current person started (409 `FROM_DATE_TOO_EARLY`). Two temporary persons cannot cover the same duty on the same day (409 `TEMPORARY_OVERLAP`). A person who is turned off, or a vehicle that is turned off, cannot be assigned. | The docs did not say. Without these rules, "who is on the vehicle" could have two answers. |
| 7 Oct 2026 | 2 | A temporary change needs `toDate` (400). A permanent change must not have one (400). | Rule 6 of the phase file. |
| 7 Oct 2026 | 2 | An assignment row cannot be ended without a replacement (there is no "remove" URL). One vehicle has one person per duty. | Phase 2 doc has only "change". Known limit. |
| 7 Oct 2026 | 2 | A person's `staffType` cannot change after they have any assignment row (409 `STAFF_TYPE_IN_USE`). | Old rows would say "driver" about someone who is no driver. |
| 7 Oct 2026 | 2 | Date text in messages: "12 Oct 2026", and for a range "12 to 16 Oct" (helper `DayText` in `common`). | The example in task 2.15 uses "12 to 16 Oct". |
| 7 Oct 2026 | 2 | `GET /vehicles` and `GET /vehicles/{id}` take an optional `?date=2026-10-14` and show the people of that day (default today). Papers are always judged against today. | The "Done when" check needs to look at a day inside and outside a leave. Phase 4 uses `?date=` in the same way. |
| 7 Oct 2026 | 2 | Services are layered so that no two services need each other. `VehicleService` is the low layer. `AssignmentService`, `StaffService` and `RouteService` call it. `VehicleOverviewService` (in `vehicle`) sits on top and adds people and the route. The vehicle controller calls only `VehicleOverviewService`. | Spring does not allow two beans that need each other. Package-by-feature rule from CLAUDE.md is kept: only services call services. |
| 7 Oct 2026 | 2 | `GET /staff` answers `worksOn` (vehicle, duty, temporary) for today. A permanent person who is replaced that day has `worksOn: null`. | The API doc says "with where they work today". |
| 7 Oct 2026 | 2 | `StudentCounts` (an interface in the `route` package) answers "how many children on this route / at this stop on day D". `ZeroStudentCounts` answers 0 until Phase 3. **Phase 3 must delete `ZeroStudentCounts`** and add the real class in the `student` package. | Rule 15 asks for the check now, and students do not exist yet. |
| 7 Oct 2026 | 2 | `PUT /routes/{id}` takes the whole route with `active`, like vehicles and staff. A route with no vehicle is allowed. A vehicle must be turned on for an active route (409 `VEHICLE_INACTIVE`). | The docs say "change name or vehicle". Turning on again needs `active`. |
| 7 Oct 2026 | 2 | Rule 2 (`VEHICLE_IN_USE`) lives in `VehicleOverviewService`, for both `DELETE` and `PUT` with `active: false`. | `VehicleService` cannot ask `RouteService` (it would be a circle). |
| 7 Oct 2026 | 2 | Stops are really deleted when they are missing from the list. **Risk for Phase 3:** a past `transport_enrolment` row may point at the stop (foreign key). Phase 3 must make `childrenByStop` count those too, or decide to turn stops off instead of deleting. | The data model has no "active" column on `route_stop`. |
| 7 Oct 2026 | 2 | The stop numbers `seq_no` are unique per route, but the check waits until the end of the transaction (deferrable). | So a re-order or a swap in one request does not break the rule in the middle. |
| 7 Oct 2026 | 2 | Load board for a route with no vehicle: `seats`, `load`, `yearlyCost`, `costPerChild` and `surplus` are null, `overBy` and `spare` are 0, verdict `NO_VEHICLE`. `feeGot` is still worked out from the children. | The docs did not say what the numbers are without a vehicle. |
| 7 Oct 2026 | 2 | Verdict `THIN` ("load below 0.6") is worked out with whole numbers (`children x 5 < seats x 3`), not from the rounded load. Example: 119 children on 200 seats show load 0.60 but are THIN. Exactly 0.6 is OK. | A rounded number must not change the verdict. |
| 7 Oct 2026 | 2 | Load board totals are for the whole fleet: `seats` and `yearlyCost` count every vehicle that is turned on, also one with no route (it still costs money). `children` and `feeGot` add up the routes. `surplus` = feeGot − yearlyCost. | The doc says "totals for the whole fleet". With the dev data (9 vehicles, 9 routes) it is 150 seats either way. |
| 7 Oct 2026 | 2 | `AttendantRouteService.routeFor(userId, date)` gives `Optional<AttendantRoute>` (route id and name, vehicle id and name). It is empty if: the user does not exist or is turned off, has no staff row, the person is turned off, is not the ATTENDANT of any vehicle that day (a temporary replacement removes the permanent attendant for those days), or the vehicle has no active route. Only the ATTENDANT duty counts, not DRIVER or HELPER. | Phase 4 turns "empty" into 403 `NOT_YOUR_ROUTE`. |
| 7 Oct 2026 | 2 | `GET /auth/me` still sends `route: null`. Phase 4 fills it from `AttendantRouteService`. | The Phase 1 code says "until Phase 4", and `AuthController` would need to call the `route` package. |
| 7 Oct 2026 | 2 | An ATTENDANT user's `staffId` must be a real staff row of type ATTENDANT: 400 `VALIDATION`, `fields.staffId` is "does not exist" or "must be a staff member of type ATTENDANT". Not checked: whether the staff member is turned on, or whether another user already uses the same staff row. | Task 2.14, changes the Phase 1 rule. The two unchecked points are not in the docs. |

## E. What changed from the first plan document

The first plan ("School Transport App — Build Plan") was written before the screens were designed. These points are replaced by this folder:

| First plan | Now |
|---|---|
| Spring Boot 3, Maven | Spring Boot 4, Java 25, Gradle |
| Login with username and password | Login with phone number and OTP |
| 2 roles (office, attendant) | 5 roles with permissions |
| 10 tables, transport only | 26 tables: transport, students, admissions, enquiries, fees |
| H2 database on the laptop | PostgreSQL everywhere |
| 7 build steps to 15 December | 10 phases. Phases 0 to 5 are the transport system. |

Still true from the first plan: parents get SMS and no app; the class rule for SMS; one tap gives one SMS; offline taps keep their own time; DLT registration must start early.
