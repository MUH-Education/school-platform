-- =====================================================================
-- V7  Phase 7: fees, month by month.
--
--   session_fee_setting  the rules of a year: bus fee, bus months, due day, late fee
--   class_fee            school fee a month for each class
--   fee_plan             what one child pays a month (changes start from a month)
--   fee_due              one row per child per month: what must be paid
--   receipt              one payment of a family (or one refund)
--   fee_payment          the lines of a receipt: child, month, SCHOOL / BUS / LATE, amount
--   family_credit_entry  money kept for a family (paid ahead, then the child left)
--
-- Paid months are never changed. Payments are never edited or deleted:
-- a cancelled receipt gets negative lines (reversal_of_id) that bring the sum back to 0.
-- =====================================================================

-- ---------------------------------------------------------------------
-- The rules of one session. One row per session.
-- ---------------------------------------------------------------------
create table session_fee_setting (
    id                      bigint generated always as identity primary key,
    session_id              bigint        not null unique references academic_session (id),
    bus_fee_per_month       numeric(12,2) not null check (bus_fee_per_month >= 0),
    -- Months with a bus fee, as month numbers. {4,5,7,8,9,10,11,12,1,2,3} = no bus fee in June.
    bus_months              int[]         not null check (bus_months <@ array[1,2,3,4,5,6,7,8,9,10,11,12] and cardinality(bus_months) <= 12),
    due_day                 int           not null default 1  check (due_day in (1, 5)),
    grace_until             int           not null default 10 check (grace_until in (10, 15, 31)),   -- 31 = end of the month
    late_fee_kind           varchar(10)   not null default 'PER_MONTH' check (late_fee_kind in ('NONE', 'PER_MONTH', 'PER_DAY')),
    late_fee_per_month      numeric(12,2) check (late_fee_per_month >= 0),
    late_fee_per_day        numeric(12,2) check (late_fee_per_day >= 0),
    late_fee_max_per_month  numeric(12,2) check (late_fee_max_per_month >= 0),
    late_fee_change_roles   varchar(30)[] not null default '{OWNER,OFFICE_ADMIN}'
                            check (late_fee_change_roles <@ array['OWNER', 'OFFICE_ADMIN', 'ADMISSIONS_DESK']::varchar(30)[]),
    fees_set_by_owner       boolean       not null default false,   -- false = copied from the year before, not checked yet
    updated_by              bigint        references app_user (id),
    created_at              timestamptz   not null default now(),
    updated_at              timestamptz   not null default now(),
    constraint session_fee_setting_per_month check (late_fee_kind <> 'PER_MONTH' or late_fee_per_month is not null),
    constraint session_fee_setting_per_day   check (late_fee_kind <> 'PER_DAY' or (late_fee_per_day is not null and late_fee_max_per_month is not null))
);

-- School fee a month for a class in a session. The admission form fills its fee from here.
create table class_fee (
    id          bigint generated always as identity primary key,
    session_id  bigint        not null references academic_session (id),
    class_name  varchar(10)   not null check (class_name in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    per_month   numeric(12,2) not null check (per_month >= 0),
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    constraint class_fee_once unique (session_id, class_name)
);

-- ---------------------------------------------------------------------
-- fee_plan: what one child pays, from a month on.
-- The plan for month M is the row with the biggest from_month that is <= M.
-- follows_class_fee = true: a change in Fee settings also changes this child's unpaid months.
-- follows_class_fee = false: the child has their own fee (changed by hand). Fee settings do not touch it.
-- The bus fee is not here: it is on transport_enrolment (it can change with the route).
-- Example: Ishaan, 2500 a month from April. From January, 250 off because his brother joins:
--   row 1: from 2026-04-01, 2500, discount 0.   row 2: from 2027-01-01, 2500, discount 250, SIBLING.
-- ---------------------------------------------------------------------
create table fee_plan (
    id                    bigint generated always as identity primary key,
    student_id            bigint        not null references student (id),
    session_id            bigint        not null references academic_session (id),
    from_month            date          not null check (extract(day from from_month) = 1),
    school_fee_per_month  numeric(12,2) not null check (school_fee_per_month >= 0),
    follows_class_fee     boolean       not null default true,
    discount_per_month    numeric(12,2) not null default 0,
    discount_reason       varchar(20)   not null default 'NONE' check (discount_reason in ('NONE', 'SIBLING', 'REFERRAL', 'STAFF_CHILD', 'OTHER')),
    pay_frequency         varchar(12)   not null default 'MONTHLY' check (pay_frequency in ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    note                  varchar(200),
    created_by            bigint        not null references app_user (id),
    created_at            timestamptz   not null default now(),
    updated_at            timestamptz   not null default now(),
    constraint fee_plan_once unique (student_id, session_id, from_month),
    constraint fee_plan_discount check (discount_per_month >= 0 and discount_per_month <= school_fee_per_month),
    constraint fee_plan_discount_reason check ((discount_per_month = 0) = (discount_reason = 'NONE'))
);
create index fee_plan_session_idx on fee_plan (session_id);

-- ---------------------------------------------------------------------
-- fee_due: one row per child per month = what must be paid for that month.
-- Made by the server from fee_plan, transport_enrolment and session_fee_setting,
-- for every month the child is in school (no row = NO_FEE).
-- When a plan, the bus or a class fee changes, the server rewrites only the UNPAID months.
-- Late fees are not here: they are counted when the family pays (and saved as LATE lines).
-- Example: Aryan, September 2026: school 2500, bus 800.  June: school 2500, bus 0.
-- ---------------------------------------------------------------------
create table fee_due (
    id               bigint generated always as identity primary key,
    student_id       bigint        not null references student (id),
    session_id       bigint        not null references academic_session (id),
    month            date          not null check (extract(day from month) = 1),
    school_amount    numeric(12,2) not null check (school_amount >= 0),    -- after the discount
    discount_amount  numeric(12,2) not null default 0 check (discount_amount >= 0),
    bus_amount       numeric(12,2) not null default 0 check (bus_amount >= 0),
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    constraint fee_due_once unique (student_id, month)
);
create index fee_due_session_month_idx on fee_due (session_id, month);

-- ---------------------------------------------------------------------
-- receipt: one payment by a family, or one refund to a family.
--   'R-2026-1189'  kind PAYMENT     'RF-2026-0003'  kind REFUND
-- amount = money that changed hands. The lines (fee_payment) say which child and month.
-- A cancelled receipt stays, with status CANCELLED and the reason. Nothing is deleted.
-- ---------------------------------------------------------------------
create table receipt (
    id                  bigint generated always as identity primary key,
    receipt_no          varchar(20)   not null unique check (receipt_no ~ '^(R|RF)-[0-9]{4}-[0-9]{4,}$'),
    kind                varchar(10)   not null default 'PAYMENT' check (kind in ('PAYMENT', 'REFUND')),
    source              varchar(10)   not null default 'COLLECT' check (source in ('COLLECT', 'ADMISSION', 'LEAVING')),
    session_id          bigint        not null references academic_session (id),
    family_guardian_id  bigint        not null references guardian (id),    -- the family (primary guardian)
    paid_on             date          not null,                               -- the school day, Asia/Kolkata
    paid_at             timestamptz   not null default now(),
    mode                varchar(15)   not null check (mode in ('CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    reference           varchar(60),                                          -- UPI reference or cheque number
    amount              numeric(12,2) not null check (amount > 0),
    credit_used         numeric(12,2) not null default 0 check (credit_used >= 0),  -- family credit used on top of the money
    late_fee_normal     numeric(12,2) not null default 0 check (late_fee_normal >= 0),
    late_fee_final      numeric(12,2) not null default 0 check (late_fee_final >= 0),
    late_fee_reason     varchar(20)   check (late_fee_reason in ('FAMILY_PROBLEM', 'CROP_MONEY_LATE', 'SCHOOL_MISTAKE', 'OWNER_DECISION', 'OTHER')),
    late_fee_note       varchar(200),                                         -- office only, never printed
    late_fee_changed_by bigint        references app_user (id),
    client_request_id   varchar(64)   unique,                                 -- double click safety
    status              varchar(10)   not null default 'ACTIVE' check (status in ('ACTIVE', 'CANCELLED')),
    cancelled_at        timestamptz,
    cancelled_by        bigint        references app_user (id),
    cancel_reason       varchar(20)   check (cancel_reason in ('WRONG_AMOUNT', 'WRONG_STUDENT', 'CHEQUE_BOUNCED', 'PAID_TWICE', 'OTHER')),
    cancel_note         varchar(300),
    taken_by            bigint        not null references app_user (id),
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    constraint receipt_number_matches_kind check ((kind = 'PAYMENT') = (receipt_no like 'R-%')),
    constraint receipt_late_fee_change_has_reason check (late_fee_final = late_fee_normal or late_fee_reason is not null),
    constraint receipt_cancel_fields check (
        (status = 'ACTIVE' and cancelled_at is null and cancel_reason is null)
        or (status = 'CANCELLED' and cancelled_at is not null and cancelled_by is not null and cancel_reason is not null and cancel_note is not null))
);
create index receipt_paid_on_idx on receipt (paid_on desc, paid_at desc);
create index receipt_family_idx  on receipt (family_guardian_id, paid_on desc);
create index receipt_session_idx on receipt (session_id);

-- ---------------------------------------------------------------------
-- fee_payment: one line of a receipt.
-- Example R-2026-1189 has 10 lines: Aryan Sep..Dec SCHOOL and BUS, Siya the same, and LATE 100 for each child for September.
-- A cancel adds one negative line for each line, with reversal_of_id. A refund has negative lines.
-- These rows are NEVER updated or deleted (a trigger stops it). In Java: @Immutable entity.
-- ---------------------------------------------------------------------
create table fee_payment (
    id              bigint generated always as identity primary key,
    receipt_id      bigint        not null references receipt (id),
    student_id      bigint        not null references student (id),
    session_id      bigint        not null references academic_session (id),
    month           date          not null check (extract(day from month) = 1),
    fee_head        varchar(10)   not null check (fee_head in ('SCHOOL', 'BUS', 'LATE')),
    amount          numeric(12,2) not null check (amount <> 0),
    reversal_of_id  bigint        unique references fee_payment (id),
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    constraint fee_payment_reversal_is_negative check (reversal_of_id is null or amount < 0)
);
create index fee_payment_student_month_idx on fee_payment (student_id, month);
create index fee_payment_receipt_idx       on fee_payment (receipt_id);
create index fee_payment_session_idx       on fee_payment (session_id, month);

-- ---------------------------------------------------------------------
-- family_credit_entry: money kept for a family. + adds credit, − uses it.
-- Example: Tanvi leaves on 31 Oct but paid up to December. The family says "keep it":
--   +6600 PAID_AHEAD_KEPT. Next time they pay for her brother: −6600 USED_IN_RECEIPT.
-- The credit of a family = the sum of its rows. Never updated or deleted.
-- ---------------------------------------------------------------------
create table family_credit_entry (
    id           bigint generated always as identity primary key,
    guardian_id  bigint        not null references guardian (id),
    amount       numeric(12,2) not null check (amount <> 0),
    reason       varchar(20)   not null check (reason in ('PAID_AHEAD_KEPT', 'USED_IN_RECEIPT', 'REFUNDED', 'CORRECTION')),
    student_id   bigint        references student (id),
    receipt_id   bigint        references receipt (id),
    note         varchar(200),
    created_by   bigint        not null references app_user (id),
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now()
);
create index family_credit_entry_guardian_idx on family_credit_entry (guardian_id);
create index family_credit_entry_student_idx  on family_credit_entry (student_id);
create index family_credit_entry_receipt_idx  on family_credit_entry (receipt_id);

-- Money rows are written once. A mistake is fixed with a new row, never by changing an old one.
create function forbid_row_change() returns trigger language plpgsql as $$
begin
    raise exception 'Rows of % are never changed or deleted. Add a new row instead.', tg_table_name
        using errcode = 'P0001';
end;
$$;
create trigger fee_payment_never_change before update or delete on fee_payment
    for each row execute function forbid_row_change();
create trigger family_credit_entry_never_change before update or delete on family_credit_entry
    for each row execute function forbid_row_change();

-- A message can belong to a receipt (the receipt on WhatsApp).
alter table message_outbox add constraint message_outbox_receipt_fk foreign key (receipt_id) references receipt (id);

-- ---------------------------------------------------------------------
-- v_fee_month: each fee_due row with what is paid for it.
-- The Java service adds the date rules (due, late, late fee) with an injected Clock.
--   balance = 0                      → PAID
--   paid > 0                         → PART_PAID
--   today > grace day of the month   → LATE  (late fee if paid now)
--   today >= due day of the month    → DUE
--   else                             → NOT_DUE
-- ---------------------------------------------------------------------
create view v_fee_month as
select d.id                                   as fee_due_id,
       d.student_id,
       d.session_id,
       d.month,
       d.school_amount,
       d.discount_amount,
       d.bus_amount,
       d.school_amount + d.bus_amount         as total,
       coalesce(p.paid_school, 0)             as paid_school,
       coalesce(p.paid_bus, 0)                as paid_bus,
       coalesce(p.paid_late, 0)               as paid_late,
       d.school_amount + d.bus_amount - coalesce(p.paid_school, 0) - coalesce(p.paid_bus, 0) as balance,
       (d.month + (s.due_day - 1))::date      as due_date,
       least(d.month + (s.grace_until - 1), (d.month + interval '1 month' - interval '1 day')::date)::date as grace_date,
       p.last_receipt_id
from fee_due d
join session_fee_setting s on s.session_id = d.session_id
left join (
    select fp.student_id,
           fp.month,
           sum(fp.amount) filter (where fp.fee_head = 'SCHOOL') as paid_school,
           sum(fp.amount) filter (where fp.fee_head = 'BUS')    as paid_bus,
           sum(fp.amount) filter (where fp.fee_head = 'LATE')   as paid_late,
           max(fp.receipt_id) filter (where fp.amount > 0 and r.status = 'ACTIVE' and r.kind = 'PAYMENT') as last_receipt_id
    from fee_payment fp
    join receipt r on r.id = fp.receipt_id
    group by fp.student_id, fp.month
) p on p.student_id = d.student_id and p.month = d.month;

-- ---------------------------------------------------------------------
-- Start values for 2026-27 (from the Fee settings design). Change them on the Fee settings page.
-- ---------------------------------------------------------------------
insert into session_fee_setting (session_id, bus_fee_per_month, bus_months, due_day, grace_until,
                                 late_fee_kind, late_fee_per_month, late_fee_per_day, late_fee_max_per_month,
                                 late_fee_change_roles, fees_set_by_owner)
select id, 800.00, '{4,5,7,8,9,10,11,12,1,2,3}', 1, 10, 'PER_MONTH', 100.00, 10.00, 300.00, '{OWNER,OFFICE_ADMIN}', true
from academic_session where name = '2026-27';

insert into class_fee (session_id, class_name, per_month)
select s.id, c.class_name, c.per_month
from academic_session s
cross join (values ('Nursery', 2200.00), ('LKG', 2300.00), ('UKG', 2500.00),
                   ('1', 2500.00), ('2', 2500.00), ('3', 2500.00), ('4', 2500.00), ('5', 2500.00),
                   ('6', 2800.00), ('7', 2800.00), ('8', 2800.00),
                   ('9', 3200.00), ('10', 3200.00), ('11', 3500.00), ('12', 3500.00)) as c(class_name, per_month)
where s.name = '2026-27';
