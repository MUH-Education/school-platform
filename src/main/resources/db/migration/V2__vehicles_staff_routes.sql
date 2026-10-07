-- Phase 2: vehicles, staff, routes.
-- Tables: vehicle, vehicle_document, staff, vehicle_assignment, route, route_stop.
-- Also the foreign key app_user.staff_id -> staff(id).
-- Details and example rows: docs/03-data-model.md → "Phase 2 tables".

-- One vehicle. Example: 'Van 4', 'HR 23 A 1104', SMALL_VAN, 14 seats, 30300.00 a month.
create table vehicle (
    id              bigint generated always as identity primary key,
    name            varchar(40)   not null,
    registration_no varchar(20)   not null,
    vehicle_type    varchar(20)   not null,
    seats           int           not null,
    monthly_cost    numeric(12,2) not null,
    owned_by        varchar(20)   not null,
    active          boolean       not null default true,
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    constraint vehicle_type_ck check (vehicle_type in ('SMALL_VAN', 'MID_BUS', 'BIG_BUS')),
    constraint vehicle_seats_ck check (seats > 0),
    constraint vehicle_monthly_cost_ck check (monthly_cost >= 0),
    constraint vehicle_owned_by_ck check (owned_by in ('SCHOOL', 'CONTRACTOR'))
);

-- Unique, ignoring capital letters and spaces: 'hr 23 a 1104' and 'HR23A1104' are the same vehicle.
-- The app keeps single spaces only (it cuts other white space), so removing ' ' is enough.
create unique index vehicle_name_uk on vehicle (upper(replace(name, ' ', '')));
create unique index vehicle_registration_no_uk on vehicle (upper(replace(registration_no, ' ', '')));

-- One paper of one vehicle. No row = no date saved (status MISSING). Status is calculated, not stored.
create table vehicle_document (
    id         bigint generated always as identity primary key,
    vehicle_id bigint      not null references vehicle (id),
    doc_type   varchar(20) not null,
    valid_till date        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint vehicle_document_type_ck check (doc_type in ('FITNESS', 'INSURANCE', 'PERMIT', 'PUC')),
    constraint vehicle_document_uk unique (vehicle_id, doc_type)
);

-- A driver, attendant or helper. Example: 'Jagdish', DRIVER, licence HR2620110012345 valid till 2029-03-31.
create table staff (
    id                 bigint generated always as identity primary key,
    name               varchar(120) not null,
    phone              varchar(13)  not null,
    staff_type         varchar(20)  not null,
    licence_no         varchar(30),
    licence_valid_till date,
    active             boolean      not null default true,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    constraint staff_phone_ck check (phone ~ '^\+91[6-9][0-9]{9}$'),
    constraint staff_type_ck check (staff_type in ('DRIVER', 'ATTENDANT', 'HELPER')),
    -- A DRIVER has a licence number and an end date. Other people have neither.
    constraint staff_licence_ck check (
        (staff_type = 'DRIVER' and licence_no is not null and licence_valid_till is not null)
        or (staff_type <> 'DRIVER' and licence_no is null and licence_valid_till is null))
);

-- The attendant login points at a staff row (the column came in V1).
alter table app_user
    add constraint app_user_staff_fk foreign key (staff_id) references staff (id);

-- Who works on which vehicle, and when. to_date null = still going on.
-- Example: Surender, Van 4, DRIVER, 12 Oct to 16 Oct, temporary (Jagdish is on leave).
create table vehicle_assignment (
    id         bigint generated always as identity primary key,
    vehicle_id bigint      not null references vehicle (id),
    staff_id   bigint      not null references staff (id),
    duty       varchar(20) not null,
    from_date  date        not null,
    to_date    date,
    temporary  boolean     not null default false,
    reason     varchar(40),
    created_by bigint references app_user (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint vehicle_assignment_duty_ck check (duty in ('DRIVER', 'ATTENDANT', 'HELPER')),
    constraint vehicle_assignment_reason_ck check (reason is null or reason in ('ON_LEAVE', 'LEFT_SCHOOL', 'MOVED', 'OTHER')),
    constraint vehicle_assignment_dates_ck check (to_date is null or to_date >= from_date),
    constraint vehicle_assignment_temporary_ck check (not temporary or to_date is not null)
);

-- "Rows of this vehicle covering day D" and "rows of this person".
create index vehicle_assignment_vehicle_ix on vehicle_assignment (vehicle_id, from_date);
create index vehicle_assignment_staff_ix on vehicle_assignment (staff_id, from_date);
create index vehicle_assignment_created_by_ix on vehicle_assignment (created_by);

-- A route is run by one vehicle. Example: 'Route 4' runs on Van 4.
create table route (
    id         bigint generated always as identity primary key,
    name       varchar(80) not null,
    vehicle_id bigint references vehicle (id),
    active     boolean     not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index route_name_uk on route (upper(replace(name, ' ', '')));
create index route_vehicle_ix on route (vehicle_id);
-- One vehicle runs at most one active route.
create unique index route_vehicle_active_uk on route (vehicle_id) where active;

-- A stop of a route, in morning order. Example: Route 4, seq 2, 'Jakhal', 07:40.
create table route_stop (
    id           bigint generated always as identity primary key,
    route_id     bigint      not null references route (id),
    name         varchar(80) not null,
    seq_no       int         not null,
    morning_time time,
    evening_time time,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint route_stop_seq_ck check (seq_no > 0),
    -- Deferred: when stops are re-ordered, the numbers may clash for a moment inside one transaction.
    constraint route_stop_seq_uk unique (route_id, seq_no) deferrable initially deferred
);
