# 3. Data model

**{{TABLES}} tables and 1 view** in PostgreSQL for the first release, plus {{LATER}} tables for marks later.
Think of each table as one register in the school office. One row is one line in that register.

This page is made by `tools/make_data_model.py` from the real database, so the columns below are always the same as the Flyway files.
Do not edit the column tables by hand: change the SQL, then run `tools/make-docs.sh`.

## Rules for every table

- `id bigint generated always as identity primary key`. In Java: `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- `created_at timestamptz not null default now()` and `updated_at timestamptz not null default now()`. In Java: `@CreationTimestamp` and `@UpdateTimestamp`.
- Two tables use a name as the key instead of an id, because they are looked up by name: `app_setting (key)` and `number_counter (counter_key)`.
- A phone is `varchar(13)`: `+91` and 10 digits. Example: `+919400000871`. The database refuses any other shape.
- Money is `numeric(12,2)`. In Java: `BigDecimal`. Never `double`.
- A **month** is a `date` on the 1st of the month: `2026-10-01` means October 2026. In Java the entity has `LocalDate`; the API shows `"2026-10"` (`YearMonth`).
- A fixed list is a `varchar` with a `check (... in (...))`. The allowed values are in the "Values" column below. In Java: an `enum` with `@Enumerated(EnumType.STRING)`.
- Every foreign key has an index, except the "who did it" columns (`created_by`, `updated_by`, `taken_by`, ...). Users are never deleted and we never search by those columns.
- People, vehicles, routes, stops and students are never deleted. They get `active = false` or a status.
- Money rows are never changed or deleted: a database trigger stops any `update` or `delete` on `fee_payment` and `family_credit_entry`. A mistake is fixed with a new row.

## Rules that live in the database, not only in Java

If the Java code has a bug, these still hold. Each one is tested in `db-checks/checks.sql`.

| Rule | How the database keeps it |
|---|---|
| One tap per child, per day, per event | `unique (student_id, service_date, event_type)` on `boarding_event` |
| "Went home with a parent" only in the evening | `check` on `boarding_event` |
| A child is on one bus (or no bus) on any day | exclusion constraint: two periods of `transport_enrolment` never share a day |
| The stop belongs to the route | two-column foreign key `(stop_id, route_id)` → `route_stop (id, route_id)` |
| One normal driver per vehicle on any day | exclusion constraint on `vehicle_assignment` |
| One vehicle runs one active route | partial unique index on `route (vehicle_id)` |
| One primary parent per child | partial unique index on `student_guardian` |
| One fee row per child per month | `unique (student_id, month)` on `fee_due` |
| Payment lines are never changed | trigger on `fee_payment` |
| The same payment click saved once | `unique (client_request_id)` on `receipt` |
| A changed late fee has a reason | `check` on `receipt` |
| One bus SMS per event per phone | partial unique index on `message_outbox` |
| One issued TC per child | partial unique index on `transfer_certificate` |
| Two sessions never share a day | exclusion constraint on `academic_session` |

The exclusion constraints need the `btree_gist` extension. V1 creates it (`create extension if not exists btree_gist`). It is a "trusted" extension, so the app's own database user can create it.

## Map of the tables

{{MAP}}

## How the tables connect, in words

- A **vehicle** has 4 papers (`vehicle_document`) and people over time (`vehicle_assignment` → `staff`).
- A **route** is run by one vehicle and has ordered stops (`route_stop`).
- A **student** has parents (`student_guardian` → `guardian`). The parent marked **primary** is the **family**: brothers and sisters with the same primary parent pay together.
- A student's bus over time is `transport_enrolment`: one row per period, "on Route 4 at Jakhal from 1 April", "no bus from 1 November".
- An attendant's tap is a `boarding_event`. A tap can make rows in `message_outbox`, one per parent phone with SMS on.
- Fees: `fee_plan` (what the child pays a month) → `fee_due` (one row per month) ← `fee_payment` (lines of a `receipt`).
- An **app_user** is a login. An attendant login points at a `staff` row. Their route today comes from today's `vehicle_assignment`.

## All tables in one picture

An arrow goes from a table to the tables that point at it. Example: `route --> route_stop` means a stop belongs to a route.
The "who did it" links to `app_user` and the links to `academic_session` are left out, so the picture stays readable.

{{OVERVIEW}}
