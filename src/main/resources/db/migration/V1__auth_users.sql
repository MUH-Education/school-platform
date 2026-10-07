-- Phase 1: login, users, roles. Tables: app_user, otp_code, audit_log, app_setting.
-- Details and example rows: docs/03-data-model.md → "Phase 1 tables".

-- A person who can log in. Example: +919812340002, 'Neelam', OFFICE_ADMIN.
create table app_user (
    id            bigint generated always as identity primary key,
    phone         varchar(13)  not null,
    name          varchar(120),
    role          varchar(30)  not null,
    staff_id      bigint,                  -- foreign key to staff comes in V2
    active        boolean      not null default true,
    token_version int          not null default 0,
    last_login_at timestamptz,
    created_by    bigint references app_user (id),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint app_user_phone_uk unique (phone),
    constraint app_user_phone_ck check (phone ~ '^\+91[6-9][0-9]{9}$'),
    constraint app_user_role_ck check (role in ('OWNER', 'OFFICE_ADMIN', 'TRANSPORT_INCHARGE', 'ADMISSIONS_DESK', 'ATTENDANT')),
    constraint app_user_attendant_staff_ck check (role <> 'ATTENDANT' or staff_id is not null),
    constraint app_user_token_version_ck check (token_version >= 0)
);

create index app_user_staff_id_ix on app_user (staff_id);
create index app_user_created_by_ix on app_user (created_by);

-- One login code that was sent. Only the hash is stored, never the code.
create table otp_code (
    id          bigint generated always as identity primary key,
    phone       varchar(13) not null,
    code_hash   varchar(64) not null,
    channel     varchar(10) not null,
    expires_at  timestamptz not null,
    attempts    int         not null default 0,
    consumed_at timestamptz,
    request_ip  varchar(45),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint otp_code_channel_ck check (channel in ('WHATSAPP', 'SMS', 'LOG')),
    constraint otp_code_attempts_ck check (attempts >= 0)
);

-- "Newest code of this phone" and "codes of this phone in the last hour".
create index otp_code_phone_created_ix on otp_code (phone, created_at desc);
-- "Codes from this IP in the last hour".
create index otp_code_ip_created_ix on otp_code (request_ip, created_at desc);

-- Who changed what. Example: USER 2, UPDATED, 'Role changed from ADMISSIONS_DESK to OFFICE_ADMIN'.
create table audit_log (
    id          bigint generated always as identity primary key,
    entity_type varchar(40)  not null,
    entity_id   bigint       not null,
    action      varchar(20)  not null,
    summary     varchar(300) not null,
    details     jsonb,
    changed_by  bigint references app_user (id),
    changed_at  timestamptz  not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint audit_log_action_ck check (action in ('CREATED', 'UPDATED', 'DELETED', 'LOGIN'))
);

create index audit_log_entity_ix on audit_log (entity_type, entity_id, changed_at desc);
create index audit_log_changed_by_ix on audit_log (changed_by);

-- School-wide numbers. Example: 'transport.bus_fee_per_year' = '8800'.
create table app_setting (
    key        varchar(60)  primary key,
    value      varchar(300) not null,
    updated_by bigint references app_user (id),
    updated_at timestamptz  not null default now()
);

create index app_setting_updated_by_ix on app_setting (updated_by);

insert into app_setting (key, value) values
    ('school.name', 'MUH Jain School'),
    ('transport.months_operated', '11'),
    ('transport.bus_fee_per_year', '8800'),
    ('transport.collection_pct', '95'),
    ('transport.late_after_minutes', '10'),
    ('fees.grace_days', '10'),
    ('fees.defaulted_after_days', '60');
