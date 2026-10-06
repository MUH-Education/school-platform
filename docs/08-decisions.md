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
| | | | |

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
