-- Phase 7: school years, standard class fees, each child's fee plan, dues, payments and receipt numbers.

-- One school year. Example: "2026-27", 1 Apr 2026 to 31 Mar 2027. Exactly one row is current.
create table academic_session (
    id         bigint generated always as identity primary key,
    name       varchar(9)  not null unique,
    starts_on  date        not null,
    ends_on    date        not null,
    is_current boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint academic_session_dates_ck check (ends_on > starts_on)
);

create unique index academic_session_one_current_uk on academic_session (is_current) where is_current;

insert into academic_session (name, starts_on, ends_on, is_current)
values ('2026-27', date '2026-04-01', date '2027-03-31', true);

-- The standard school fee of a class in a session. Example: session 2026-27, class 3, 30000.00.
create table class_fee (
    id         bigint generated always as identity primary key,
    session_id bigint        not null references academic_session (id),
    class_name varchar(10)   not null,
    school_fee numeric(12,2) not null,
    created_at timestamptz   not null default now(),
    updated_at timestamptz   not null default now(),
    constraint class_fee_uk unique (session_id, class_name),
    constraint class_fee_class_ck check (class_name in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', '10', '11', '12')),
    constraint class_fee_amount_ck check (school_fee >= 0)
);

-- What one child must pay in one session. Example: school 30000, bus 8800, discount 0, QUARTERLY.
create table fee_plan (
    id              bigint generated always as identity primary key,
    student_id      bigint        not null references student (id),
    session_id      bigint        not null references academic_session (id),
    school_fee      numeric(12,2) not null,
    bus_fee         numeric(12,2) not null default 0,
    discount        numeric(12,2) not null default 0,
    discount_reason varchar(20)   not null default 'NONE',
    pay_frequency   varchar(12)   not null,
    created_by      bigint references app_user (id),
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    constraint fee_plan_uk unique (student_id, session_id),
    constraint fee_plan_amounts_ck check (school_fee >= 0 and bus_fee >= 0 and discount >= 0
        and discount <= school_fee),
    constraint fee_plan_reason_ck check (discount_reason in ('NONE', 'SIBLING', 'REFERRAL', 'STAFF_CHILD', 'OTHER')),
    constraint fee_plan_discount_reason_ck check (discount = 0 or discount_reason <> 'NONE'),
    constraint fee_plan_frequency_ck check (pay_frequency in ('MONTHLY', 'QUARTERLY', 'YEARLY'))
);

create index fee_plan_session_ix on fee_plan (session_id);
create index fee_plan_created_by_ix on fee_plan (created_by);

-- One amount that must be paid by one date. Example: SCHOOL, 1 Jul 2026, 7500.00.
create table fee_due (
    id          bigint generated always as identity primary key,
    fee_plan_id bigint        not null references fee_plan (id),
    fee_head    varchar(10)   not null,
    due_on      date          not null,
    amount      numeric(12,2) not null,
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    constraint fee_due_head_ck check (fee_head in ('SCHOOL', 'BUS')),
    constraint fee_due_amount_ck check (amount > 0)
);

create index fee_due_plan_ix on fee_due (fee_plan_id, fee_head, due_on);

-- Money the office received. Never edited, never deleted. A correction is a new row below 0 with a note.
-- Example: SCHOOL 7500.00 by UPI on 1 Apr, receipt R-2026-0412.
create table fee_payment (
    id          bigint generated always as identity primary key,
    student_id  bigint        not null references student (id),
    session_id  bigint        not null references academic_session (id),
    fee_head    varchar(10)   not null,
    amount      numeric(12,2) not null,
    paid_on     date          not null,
    mode        varchar(15)   not null,
    receipt_no  varchar(20)   not null,
    note        varchar(200),
    recorded_by bigint references app_user (id),
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    constraint fee_payment_head_ck check (fee_head in ('SCHOOL', 'BUS')),
    constraint fee_payment_amount_ck check (amount <> 0),
    constraint fee_payment_mode_ck check (mode in ('CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE')),
    constraint fee_payment_correction_note_ck check (amount > 0 or note is not null)
);

create index fee_payment_student_ix on fee_payment (student_id, session_id, fee_head);
create index fee_payment_paid_on_ix on fee_payment (paid_on, id);
create index fee_payment_receipt_ix on fee_payment (receipt_no);
create index fee_payment_session_ix on fee_payment (session_id);
create index fee_payment_recorded_by_ix on fee_payment (recorded_by);

-- One row per session. The server adds 1 and builds R-2026-0412. Example: session 2026-27, last_no 411.
create table receipt_counter (
    session_id bigint      primary key references academic_session (id),
    last_no    int         not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
