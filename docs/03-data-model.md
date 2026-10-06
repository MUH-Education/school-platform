# 3. Data model

26 tables in PostgreSQL. Think of each table as one register in the school office. One row is one line in that register.

## Rules for every table

- `id bigint generated always as identity primary key`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`
- Phone numbers: `varchar(13)`, always `+91` and 10 digits. Example: `+919812345678`.
- Money: `numeric(12,2)`.
- A fixed list is a `varchar` with a `check` constraint. The allowed values are written in CAPITALS below.
- "→ table" means a foreign key to that table. Every foreign key has an index.

The three standard columns are not repeated in the tables below.

## Map of the tables

| Phase | Flyway file | Tables |
|---|---|---|
| 1 | `V1__auth_users.sql` | `app_user`, `otp_code`, `audit_log`, `app_setting` |
| 2 | `V2__vehicles_staff_routes.sql` | `vehicle`, `vehicle_document`, `staff`, `vehicle_assignment`, `route`, `route_stop` |
| 3 | `V3__students.sql` | `student`, `guardian`, `student_guardian`, `transport_enrolment`, `admission_counter` |
| 4 | `V4__trips.sql` | `boarding_event` |
| 5 | `V5__messaging.sql` | `message_template`, `message_outbox` |
| 6 | `V6__enquiries.sql` | `enquiry`, `enquiry_follow_up` |
| 7 | `V7__fees.sql` | `academic_session`, `class_fee`, `fee_plan`, `fee_due`, `fee_payment`, `receipt_counter` |

How they connect, in words:

- A **vehicle** has papers (`vehicle_document`) and people over time (`vehicle_assignment` → `staff`).
- A **route** is run by one vehicle and has ordered stops (`route_stop`).
- A **student** has many phone numbers (`student_guardian` → `guardian`) and a bus history (`transport_enrolment` → `route`, `route_stop`).
- An attendant's tap is a `boarding_event` for one student on one day.
- Each tap can create rows in `message_outbox`, one per parent phone.
- An **app_user** is a login. An attendant login points at a `staff` row.

---

## Phase 1 tables

### `app_user` — a person who can log in

| Column | Type | Notes |
|---|---|---|
| `phone` | varchar(13), unique, not null | The login id |
| `name` | varchar(120), null | Optional. Shown in "who changed this". |
| `role` | varchar(30), not null | OWNER, OFFICE_ADMIN, TRANSPORT_INCHARGE, ADMISSIONS_DESK, ATTENDANT |
| `staff_id` | bigint, null → `staff` | Only for ATTENDANT. The foreign key is added in V2. |
| `active` | boolean, default true | false = cannot log in |
| `token_version` | int, default 0 | +1 on logout or when turned off. Old tokens stop working. |
| `last_login_at` | timestamptz, null | |
| `created_by` | bigint, null → `app_user` | |

Example row: `+919812340001`, "Balwan", ATTENDANT, staff 14, active, version 0.

### `otp_code` — one login code that was sent

| Column | Type | Notes |
|---|---|---|
| `phone` | varchar(13), not null | |
| `code_hash` | varchar(64), not null | HMAC-SHA256 of the code. Never the code itself. |
| `channel` | varchar(10), not null | WHATSAPP, SMS, LOG |
| `expires_at` | timestamptz, not null | created + 5 minutes |
| `attempts` | int, default 0 | wrong tries so far |
| `consumed_at` | timestamptz, null | set when used |
| `request_ip` | varchar(45), null | |

Index: `(phone, created_at desc)`. Only the newest unused row for a phone is valid. A nightly job deletes rows older than 7 days.

### `audit_log` — who changed what

| Column | Type | Notes |
|---|---|---|
| `entity_type` | varchar(40), not null | STUDENT, VEHICLE, USER, ROUTE, ... |
| `entity_id` | bigint, not null | |
| `action` | varchar(20), not null | CREATED, UPDATED, DELETED, LOGIN |
| `summary` | varchar(300), not null | One human line: "Section changed from B to A" |
| `details` | jsonb, null | old and new values |
| `changed_by` | bigint, null → `app_user` | |
| `changed_at` | timestamptz, not null | |

Index: `(entity_type, entity_id, changed_at desc)`. This feeds the "Change history" box on the student page.

### `app_setting` — school-wide numbers

Primary key is `key varchar(60)`. Columns: `value varchar(300)`, `updated_by`, `updated_at`.

| Key | Start value | Used by |
|---|---|---|
| `school.name` | MUH Jain School | SMS text |
| `transport.months_operated` | 11 | Routes and load |
| `transport.bus_fee_per_year` | 8800 | Routes and load, admission form |
| `transport.collection_pct` | 95 | Routes and load |
| `transport.late_after_minutes` | 10 | Bus status "late" |
| `fees.grace_days` | 10 | Fee status |
| `fees.defaulted_after_days` | 60 | Fee status |

---

## Phase 2 tables

### `vehicle`

| Column | Type | Notes |
|---|---|---|
| `name` | varchar(40), unique | "Van 4" |
| `registration_no` | varchar(20), unique | |
| `vehicle_type` | varchar(20) | SMALL_VAN, MID_BUS, BIG_BUS |
| `seats` | int, check > 0 | 14 |
| `monthly_cost` | numeric(12,2) | 30300.00, all-in |
| `owned_by` | varchar(20) | SCHOOL, CONTRACTOR |
| `active` | boolean | |

### `vehicle_document` — one paper of one vehicle

| Column | Type | Notes |
|---|---|---|
| `vehicle_id` | → `vehicle` | |
| `doc_type` | varchar(20) | FITNESS, INSURANCE, PERMIT, PUC |
| `valid_till` | date | last valid day |

Unique: `(vehicle_id, doc_type)`. Status is calculated, not stored: before today = ENDED, within 30 days = ENDING_SOON, else VALID.

### `staff` — driver, attendant or helper

| Column | Type | Notes |
|---|---|---|
| `name` | varchar(120) | |
| `phone` | varchar(13) | |
| `staff_type` | varchar(20) | DRIVER, ATTENDANT, HELPER |
| `licence_no` | varchar(30), null | drivers only |
| `licence_valid_till` | date, null | drivers only |
| `active` | boolean | |

### `vehicle_assignment` — who works on which vehicle, and when

| Column | Type | Notes |
|---|---|---|
| `vehicle_id` | → `vehicle` | |
| `staff_id` | → `staff` | |
| `duty` | varchar(20) | DRIVER, ATTENDANT, HELPER |
| `from_date` | date | first day |
| `to_date` | date, null | last day. null = still going on |
| `temporary` | boolean, default false | true = a replacement for some days |
| `reason` | varchar(40), null | ON_LEAVE, LEFT_SCHOOL, MOVED, OTHER |
| `created_by` | → `app_user` | |

Checks: `to_date is null or to_date >= from_date`. A temporary row must have a `to_date`.

**Who is on the vehicle on day D?** For each duty: take the temporary row that covers D if there is one. Otherwise take the normal row that covers D.

Example: Jagdish drives Van 4 from 1 Apr 2026, `to_date` null. He is on leave 12 to 16 Oct. We add one row: Surender, Van 4, DRIVER, 12 Oct to 16 Oct, temporary. On 14 Oct the driver is Surender. On 17 Oct it is Jagdish again. We never changed Jagdish's row.

Rules checked in the service:
- One normal DRIVER and one normal ATTENDANT per vehicle at a time.
- One person cannot be on two vehicles on the same day.

### `route`

| Column | Type | Notes |
|---|---|---|
| `name` | varchar(80), unique | "Route 4" |
| `vehicle_id` | bigint, null → `vehicle` | the vehicle that runs it |
| `active` | boolean | |

One vehicle runs at most one active route (partial unique index on `vehicle_id` where `active`).

### `route_stop`

| Column | Type | Notes |
|---|---|---|
| `route_id` | → `route` | |
| `name` | varchar(80) | village or place: "Jakhal" |
| `seq_no` | int | 1, 2, 3 in morning order |
| `morning_time` | time, null | 07:40 |
| `evening_time` | time, null | |

The evening order is the morning order reversed.

---

## Phase 3 tables

### `student`

| Column | Type | Notes |
|---|---|---|
| `admission_no` | varchar(20), unique | "A-2026-118", made by the server |
| `name` | varchar(120) | |
| `dob` | date | |
| `gender` | char(1) | M, F. Needed for Hindi SMS wording. |
| `class_name` | varchar(10) | Nursery, LKG, UKG, 1 … 12 |
| `section` | varchar(4), null | A, B |
| `village` | varchar(80) | |
| `address` | varchar(200), null | |
| `father_occupation` | varchar(30) | FARMER_SMALL, FARMER_LARGE, GOVT_EMPLOYEE, EX_SERVICEMAN, SHOPKEEPER, PRIVATE_JOB, LABOUR, TEACHER_PROFESSIONAL, FAMILY_ABROAD, OTHER |
| `status` | varchar(10) | ACTIVE, LEFT |
| `joined_on` | date | |
| `left_on` | date, null | |
| `photo_key` | varchar(200), null | key in photo storage |
| `created_by` | → `app_user` | |

### `guardian` — one phone number of a parent or relative

| Column | Type | Notes |
|---|---|---|
| `name` | varchar(120), null | |
| `phone` | varchar(13), unique | |
| `preferred_language` | varchar(5), default 'hi' | |

One phone = one row, even if it belongs to three children.

### `student_guardian` — this phone belongs to this child

| Column | Type | Notes |
|---|---|---|
| `student_id` | → `student` | |
| `guardian_id` | → `guardian` | |
| `relation` | varchar(20) | FATHER, MOTHER, GRANDFATHER, GRANDMOTHER, UNCLE_AUNT, OTHER |
| `sms_enabled` | boolean, default true | "Send bus SMS to this number" |
| `is_primary` | boolean, default false | |

Unique: `(student_id, guardian_id)`.

Example: Aryan and Siya are brother and sister. Their mother's phone is one `guardian` row. It has two `student_guardian` rows, one for each child.

### `transport_enrolment` — a child's bus history

| Column | Type | Notes |
|---|---|---|
| `student_id` | → `student` | |
| `route_id` | → `route` | |
| `stop_id` | → `route_stop` | |
| `from_date` | date | first day on this route |
| `to_date` | date, null | last day. null = still going on |
| `bus_fee` | numeric(12,2), null | fee agreed for this period |
| `created_by` | → `app_user` | |

Partial unique index: one open row (`to_date is null`) per student.

A child with no row uses no bus. "On the bus on day D" means `from_date <= D and (to_date is null or to_date >= D)`.

Examples:
- **Start the bus later.** Ishaan joined on 1 Apr with no bus. On 2 Nov he starts Route 9. Insert one row: Route 9, Model Town stop, `from_date` 2 Nov.
- **Change route.** A child moves from Route 4 to Route 6 on 1 Dec. Set `to_date = 30 Nov` on the old row. Insert a new row from 1 Dec.
- **Stop the bus.** Set `to_date` on the open row.

### `admission_counter`

One row per year: `year int primary key`, `last_no int`. The server locks the row, adds 1, and builds `A-2026-119`.

---

## Phase 4 table

### `boarding_event` — one tap

| Column | Type | Notes |
|---|---|---|
| `student_id` | → `student` | |
| `route_id` | → `route` | the route on that day |
| `service_date` | date | the school day |
| `event_type` | varchar(20) | BOARDED_MORNING, REACHED_SCHOOL, BOARDED_EVENING, REACHED_HOME |
| `outcome` | varchar(20) | DONE, ABSENT, NOT_TRAVELLING |
| `occurred_at` | timestamptz | time of the tap **on the phone** |
| `recorded_by` | → `app_user` | |
| `recorded_at` | timestamptz | time the server got it |

**Unique: `(student_id, service_date, event_type)`.** This is the most important constraint in the system. A double tap, or a tap sent again after bad signal, updates the same row.

Index: `(service_date, route_id)`.

`NOT_TRAVELLING` is only for BOARDED_EVENING. It means "went home with a parent, not by bus".

Example row: Aryan, Route 4, 7 Oct 2026, BOARDED_MORNING, DONE, 07:42:10, by Balwan.

---

## Phase 5 tables

### `message_template`

Primary key `code varchar(40)`. Example codes: `BOARDED_MORNING_M`, `BOARDED_MORNING_F`.

| Column | Type | Notes |
|---|---|---|
| `channel` | varchar(10) | SMS, WHATSAPP |
| `language` | varchar(5) | hi |
| `body` | varchar(500) | with `{name}` and `{time}` |
| `provider_template_id` | varchar(60), null | the id the SMS company gives after approval |
| `active` | boolean | |

### `message_outbox` — every message to a parent

| Column | Type | Notes |
|---|---|---|
| `purpose` | varchar(20) | BOARDING, ENQUIRY, FEE_REMINDER, OTHER |
| `channel` | varchar(10) | SMS, WHATSAPP |
| `phone` | varchar(13) | |
| `guardian_id` | bigint, null → `guardian` | |
| `student_id` | bigint, null → `student` | |
| `service_date` | date, null | |
| `event_type` | varchar(20), null | |
| `template_code` | varchar(40), null | |
| `body` | varchar(500) | final text |
| `status` | varchar(12) | QUEUED, SENT, FAILED, TEST_ONLY |
| `attempts` | int, default 0 | |
| `provider_ref` | varchar(80), null | id from the provider |
| `error` | varchar(300), null | |
| `sent_at` | timestamptz, null | |

Partial unique index for boarding messages: `(guardian_id, student_id, service_date, event_type) where purpose = 'BOARDING'`. So one event gives one SMS per phone, whatever happens.

Index: `(status, id)` for the sending job.

OTP messages are **not** stored here. They are sent at once and recorded in `otp_code`.

---

## Phase 6 tables

### `enquiry`

| Column | Type | Notes |
|---|---|---|
| `parent_name` | varchar(120) | |
| `phone` | varchar(13) | |
| `relation` | varchar(20), null | |
| `village` | varchar(80) | |
| `child_name` | varchar(120), null | |
| `class_sought` | varchar(10) | |
| `child_age` | varchar(20), null | free text: "6 years" |
| `current_school` | varchar(120), null | |
| `source` | varchar(20) | WALK_IN, REFERRAL, FACEBOOK, WHATSAPP, HOARDING, BUS_ENQUIRY |
| `referred_by` | varchar(120), null | name typed by the office |
| `referred_by_guardian_id` | bigint, null → `guardian` | if matched to a known parent |
| `needs_bus` | varchar(10) | YES, NO, UNKNOWN |
| `status` | varchar(12) | NEW, CONTACTED, VISITED, APPLIED, ADMITTED, LOST |
| `lost_reason` | varchar(200), null | needed when LOST |
| `next_follow_up_on` | date, null | |
| `note` | varchar(1000), null | |
| `session_name` | varchar(9) | "2027-28" |
| `admitted_student_id` | bigint, null → `student` | filled when admitted |
| `created_by` | → `app_user` | |

"Overdue" is calculated: `next_follow_up_on < today` and status is not ADMITTED or LOST.

### `enquiry_follow_up`

`enquiry_id` → `enquiry`, `note varchar(1000)`, `next_action_on date null`, `created_by`. One row per call or visit.

---

## Phase 7 tables

### `academic_session`

`name varchar(9) unique` ("2026-27"), `starts_on date`, `ends_on date`, `is_current boolean`. Only one current row (partial unique index).

### `class_fee` — the standard school fee for a class in a session

`session_id`, `class_name`, `school_fee numeric(12,2)`. Unique `(session_id, class_name)`. The admission form fills its fee from here.

### `fee_plan` — what one child must pay in one session

| Column | Type | Notes |
|---|---|---|
| `student_id` | → `student` | |
| `session_id` | → `academic_session` | |
| `school_fee` | numeric(12,2) | for the year |
| `bus_fee` | numeric(12,2) | for the year, 0 if no bus |
| `discount` | numeric(12,2), default 0 | taken off the school fee |
| `discount_reason` | varchar(20) | NONE, SIBLING, REFERRAL, STAFF_CHILD, OTHER |
| `pay_frequency` | varchar(12) | MONTHLY, QUARTERLY, YEARLY |
| `created_by` | → `app_user` | |

Unique: `(student_id, session_id)`.

### `fee_due` — one amount that must be paid by one date

`fee_plan_id` → `fee_plan`, `fee_head` (SCHOOL, BUS), `due_on date`, `amount numeric(12,2)`.

The server creates these rows from the plan. Example: school fee ₹30,000, QUARTERLY → four SCHOOL rows of ₹7,500 due on 1 Apr, 1 Jul, 1 Oct, 1 Jan.

### `fee_payment` — money the office received

| Column | Type | Notes |
|---|---|---|
| `student_id` | → `student` | |
| `session_id` | → `academic_session` | |
| `fee_head` | varchar(10) | SCHOOL, BUS |
| `amount` | numeric(12,2), check <> 0 | Normally above 0. A correction row is below 0. |
| `paid_on` | date | |
| `mode` | varchar(15) | CASH, UPI, BANK_TRANSFER, CHEQUE |
| `receipt_no` | varchar(20) | "R-2026-0412". Two rows share it when one receipt covers school and bus. |
| `note` | varchar(200), null | |
| `recorded_by` | → `app_user` | |

Payments are never edited or deleted. A mistake is fixed with a new correction row (a negative amount and a note). Only the owner can add a correction (`FEES_CORRECT`).

### `receipt_counter`

`session_id` primary key, `last_no int`.

### How fee status is calculated (not stored)

For one child, one fee head, on day D:

1. `due = sum(fee_due.amount where due_on <= D)`
2. `paid = sum(fee_payment.amount)`
3. `pending = max(0, due - paid)`
4. If `pending = 0` → **ON_TIME**.
5. Else find the oldest due that is not fully covered (payments cover dues oldest first). `days_late = D - that due date`.
   - `days_late <= fees.grace_days` → ON_TIME
   - `days_late <= fees.defaulted_after_days` → DELAYED
   - more → DEFAULTED

The child's overall status is the worse of SCHOOL and BUS.

Example on 7 Oct: dues were ₹7,500 on 1 Apr, 1 Jul, 1 Oct. The family paid ₹15,000. The 1 Oct due is not covered. It is 6 days late, inside the 10 grace days. Status: ON_TIME, pending ₹7,500.

---

## Three queries used everywhere

Write each one once, in a service, and reuse it.

1. **Children on route R on day D** — `transport_enrolment` rows of R that cover D, joined to ACTIVE students. Used by Routes and load, the manifest, Bus status.
2. **People on vehicle V on day D** — the assignment rule above. Used by Vehicles, Bus status, attendant login.
3. **The attendant's route today** — user → `staff_id` → vehicle where that staff is ATTENDANT today → the active route of that vehicle. Used to protect every trip endpoint.
