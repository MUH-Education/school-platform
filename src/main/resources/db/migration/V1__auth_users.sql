-- =====================================================================
-- V1  Phase 1: logins, login codes, change history, school settings,
--     academic sessions.
--
-- Rules for every table (see docs/03-data-model.md):
--   id          bigint generated always as identity primary key
--   created_at  timestamptz not null default now()
--   updated_at  timestamptz not null default now()   (Hibernate @UpdateTimestamp sets it)
--   A fixed list is varchar + check (...). In Java: enum + @Enumerated(STRING).
--   Money is numeric(12,2). Phone is varchar(13): +91 and 10 digits.
--   Every foreign key has an index, except the "who did it" columns
--   (created_by, updated_by, ...): users are never deleted and we never search by them.
-- =====================================================================

-- Needed for the "no two rows for the same days" rules (exclude using gist).
-- It is a trusted extension: the app's own database user can create it.
create extension if not exists btree_gist;

-- ---------------------------------------------------------------------
-- academic_session: one school year, 1 April to 31 March.
-- "Current" is not stored: it is the session whose dates include today.
-- (Moved here from V7, because admissions (V3) and enquiries (V6) need it.)
-- ---------------------------------------------------------------------
create table academic_session (
    id          bigint generated always as identity primary key,
    name        varchar(7)  not null unique check (name ~ '^[0-9]{4}-[0-9]{2}$'),  -- '2026-27'
    starts_on   date        not null,
    ends_on     date        not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint academic_session_dates check (ends_on > starts_on),
    constraint academic_session_no_overlap exclude using gist (daterange(starts_on, ends_on, '[]') with &&)
);

-- ---------------------------------------------------------------------
-- app_user: a person who can log in (phone + 6-digit code, no password).
-- ---------------------------------------------------------------------
create table app_user (
    id                 bigint generated always as identity primary key,
    phone              varchar(13) not null unique check (phone ~ '^\+91[6-9][0-9]{9}$'),
    name               varchar(120),
    role               varchar(30) not null check (role in ('OWNER', 'OFFICE_ADMIN', 'TRANSPORT_INCHARGE', 'ADMISSIONS_DESK', 'ATTENDANT')),
    staff_id           bigint,                         -- foreign key added in V2
    active             boolean     not null default true,
    token_version      int         not null default 0,  -- +1 on logout, phone change, role change, turn off
    last_login_at      timestamptz,
    last_login_client  varchar(20) check (last_login_client in ('WEB', 'ATTENDANT_APP')),
    created_by         bigint references app_user (id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint app_user_attendant_has_staff check (role <> 'ATTENDANT' or staff_id is not null)
);

-- ---------------------------------------------------------------------
-- otp_code: one login code that was sent. Only the HMAC of the code is kept.
-- A nightly job deletes rows older than 7 days.
-- ---------------------------------------------------------------------
create table otp_code (
    id           bigint generated always as identity primary key,
    phone        varchar(13) not null,
    code_hash    varchar(64) not null,                -- HMAC-SHA256, hex. Never the code.
    channel      varchar(10) not null check (channel in ('WHATSAPP', 'SMS', 'LOG')),
    expires_at   timestamptz not null,                 -- created + 5 minutes
    attempts     int         not null default 0 check (attempts between 0 and 5),
    consumed_at  timestamptz,
    request_ip   varchar(45),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index otp_code_phone_idx on otp_code (phone, created_at desc);
create index otp_code_ip_idx    on otp_code (request_ip, created_at desc);

-- ---------------------------------------------------------------------
-- audit_log: who changed what. Feeds "Change history" on the student page.
-- Also records every time a private document is opened (action OPENED).
-- ---------------------------------------------------------------------
create table audit_log (
    id           bigint generated always as identity primary key,
    entity_type  varchar(40)  not null,   -- STUDENT, VEHICLE, USER, ROUTE, RECEIPT, DOCUMENT, FEE_SETTING, ...
    entity_id    bigint       not null,
    action       varchar(20)  not null check (action in ('CREATED', 'UPDATED', 'DELETED', 'LOGIN', 'OPENED', 'CANCELLED')),
    summary      varchar(300) not null,   -- one line for people: 'Section changed from B to A'
    details      jsonb,                   -- old and new values
    changed_by   bigint references app_user (id),
    changed_at   timestamptz  not null default now(),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);
create index audit_log_entity_idx on audit_log (entity_type, entity_id, changed_at desc);

-- ---------------------------------------------------------------------
-- app_setting: school-wide values, one row per key.
-- The key is the primary key (an exception to the id rule: it is a lookup by name).
-- ---------------------------------------------------------------------
create table app_setting (
    key         varchar(60)  primary key,
    value       varchar(300) not null,
    updated_by  bigint references app_user (id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

insert into app_setting (key, value) values
    ('school.name',                  'MUH Jain Global School'),
    ('school.short_name',            'MUH Jain School'),            -- used at the end of SMS texts
    ('school.address',               'Tohana, Fatehabad district, Haryana'),
    ('school.office_phone',          '+919800000100'),              -- change on School settings
    ('school.principal_name',        ''),
    ('school.affiliation_no',        ''),
    ('school.school_code',           ''),
    ('transport.months_operated',    '11'),
    ('transport.collection_pct',     '95'),
    ('transport.late_after_minutes', '10'),
    ('fees.defaulted_after_days',    '60');

-- The current session. Add the next one from Fee settings (POST /sessions).
insert into academic_session (name, starts_on, ends_on) values ('2026-27', '2026-04-01', '2027-03-31');
