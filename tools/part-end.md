## Java entities: the tricky types

`spring.jpa.hibernate.ddl-auto=validate` checks every entity against these tables. These types need a line more than usual:

| Column | Java field |
|---|---|
| `bigint generated always as identity` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id` |
| `numeric(12,2)` | `@Column(precision = 12, scale = 2) BigDecimal amount` |
| `timestamptz` | `OffsetDateTime` (or `Instant`) |
| `date` | `LocalDate` (a month column too: `2026-10-01`) |
| `time` | `LocalTime` |
| `varchar` with a check list | `@Enumerated(EnumType.STRING) @Column(length = 20) Role role` |
| `int[]` (`session_fee_setting.bus_months`) | `@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "int[]") Integer[] busMonths` |
| `varchar(30)[]` (`late_fee_change_roles`) | `@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "varchar(30)[]") String[] lateFeeChangeRoles` |
| `jsonb` (`admission_draft.form`, `audit_log.details`) | `@JdbcTypeCode(SqlTypes.JSON) Map<String, Object> form` |
| `fee_payment`, `family_credit_entry` | add `@org.hibernate.annotations.Immutable` to the entity: Hibernate never sends an update |
| `v_fee_month` (a view) | do not make it an entity (validate would look for a table). Read it with `JdbcClient` or a native query into a record |

Relations to other features are ids, not objects: `Long routeId`, not `@ManyToOne Route route` (see `docs/02-architecture.md`).

## Sample data and checks

| File | What it is |
|---|---|
| `src/main/resources/db/dev/sample-data.sql` | The people and numbers of the designs: Punia family, Route 4 with 19 children, the receipts of 7 Oct, Kavya Goyal's admission, 290 students in all. It deletes the data first, so it runs only with `-v dev=1`. |
| `db-checks/checks.sql` | {{CHECKS}} checks: the numbers of the designs (Punia owes 15,400; 46,000 collected by 11:30; Route 4 has 19 children) and {{REFUSED}} kinds of bad data the database must refuse. Nothing is saved. |
| `db-checks/run-checks.sh` | Makes a fresh database `school_check`, runs every migration, the sample data and the checks. |

Run it against the PostgreSQL of `compose.yaml`:

```bash
PGHOST=localhost PGPORT=5432 PGUSER=school PGPASSWORD=school ./db-checks/run-checks.sh
```

The last line must be `All checks passed. Nothing was saved.`

## What changed from the old data model

| Old | New | Why |
|---|---|---|
| `academic_session` in V7 | in V1 | Admissions (V3) and enquiries (V6) need it |
| `admission_counter`, `receipt_counter` | one `number_counter` (admission, receipt, TC, refund) | One way to make every number |
| `enquiry.session_name` text | `enquiry.session_id` → `academic_session` | |
| `transport_enrolment.bus_fee` for a period | `bus_fee_per_month`, `uses_bus`, `reason`, `note`; "no bus" is a period too | Bus fee a month, undo of a change, history |
| `class_fee.school_fee` for the year | `class_fee.per_month` | Fees are month by month |
| `fee_plan` one row per year, year amounts | rows from a month on, month amounts, `follows_class_fee` | "A change starts from the next unpaid month" |
| `fee_due` per installment date | one row per child per month (school, discount, bus) | The 12 month boxes of Collect fees |
| `fee_payment` per head, with `receipt_no` text | `receipt` table + lines per child, month and head (SCHOOL / BUS / LATE) | One receipt for the family, cancel, late fee |
| correction rows by hand | cancel = reversal lines with `reversal_of_id` | Cancel a receipt |
| `app_setting` `transport.bus_fee_per_year`, `fees.grace_days` | in `session_fee_setting` | They can change per year |
| `guardian.phone` always filled | can be empty (name only) | "Mother, no number saved" |
| `message_template` code with `_M` / `_F` | `code` + `gender`, plus `approved_body` and `approval` | Boy and girl texts, DLT approval |
| `message_outbox` status QUEUED/SENT/FAILED | + DELIVERED, CANCELLED, `problem`, `receipt_id`, `fallback_of_id` | Messages page, receipts on WhatsApp |
| — | new: `holiday`, `bus_sms_rule`, `upload`, `student_document`, `student_document_note`, `admission_draft`, `transfer_certificate`, `enquiry_stage`, `session_fee_setting`, `receipt`, `family_credit_entry`, `promotion_run`, `promotion_choice` | The new screens |
