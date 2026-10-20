-- =====================================================================
-- V2  Phase 2: vehicles, their papers, staff, who works on which
--     vehicle and when, routes and stops.
-- =====================================================================

create table vehicle (
    id               bigint generated always as identity primary key,
    name             varchar(40)   not null unique,               -- 'Van 4'
    registration_no  varchar(20)   not null unique,               -- 'HR 23 AB 1104'
    vehicle_type     varchar(20)   not null check (vehicle_type in ('SMALL_VAN', 'MID_BUS', 'BIG_BUS')),
    seats            int           not null check (seats > 0),
    monthly_cost     numeric(12,2) not null default 0 check (monthly_cost >= 0),   -- all-in: 30300.00
    owned_by         varchar(20)   not null default 'SCHOOL' check (owned_by in ('SCHOOL', 'CONTRACTOR')),
    active           boolean       not null default true,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now()
);

-- One paper of one vehicle. The server makes the 4 rows when a vehicle is added (valid_till empty).
-- Status is counted, not stored: empty = MISSING, before today = ENDED, 30 days or less = ENDING_SOON, else VALID.
create table vehicle_document (
    id          bigint generated always as identity primary key,
    vehicle_id  bigint      not null references vehicle (id),
    doc_type    varchar(20) not null check (doc_type in ('FITNESS', 'INSURANCE', 'PERMIT', 'PUC')),
    valid_till  date,                                       -- the last valid day
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint vehicle_document_one_per_type unique (vehicle_id, doc_type)
);

create table staff (
    id                  bigint generated always as identity primary key,
    name                varchar(120) not null,
    phone               varchar(13)  not null check (phone ~ '^\+91[6-9][0-9]{9}$'),
    staff_type          varchar(20)  not null check (staff_type in ('DRIVER', 'ATTENDANT', 'HELPER')),
    licence_no          varchar(30),
    licence_valid_till  date,
    active              boolean      not null default true,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    constraint staff_driver_has_licence check (staff_type <> 'DRIVER' or (licence_no is not null and licence_valid_till is not null))
);

-- Who works on which vehicle, and when.
-- A normal row: from_date, to_date empty = still going on.
-- A temporary row: a replacement for some days (to_date is needed). It wins over the normal row on those days.
-- Example: Jagdish drives Van 4 from 1 Apr 2026 (normal). Surender drives 12 to 16 Oct (temporary).
create table vehicle_assignment (
    id          bigint generated always as identity primary key,
    vehicle_id  bigint      not null references vehicle (id),
    staff_id    bigint      not null references staff (id),
    duty        varchar(20) not null check (duty in ('DRIVER', 'ATTENDANT', 'HELPER')),
    from_date   date        not null,
    to_date     date,
    temporary   boolean     not null default false,
    reason      varchar(20) check (reason in ('ON_LEAVE', 'LEFT_SCHOOL', 'MOVED', 'OTHER')),
    created_by  bigint      not null references app_user (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint vehicle_assignment_dates check (to_date is null or to_date >= from_date),
    constraint vehicle_assignment_temporary_has_end check (not temporary or to_date is not null),
    -- One normal driver (and one normal attendant, one normal helper) per vehicle on any day.
    constraint vehicle_assignment_one_normal exclude using gist (
        vehicle_id with =, duty with =, daterange(from_date, to_date, '[]') with &&) where (not temporary),
    -- One replacement per vehicle and duty on any day.
    constraint vehicle_assignment_one_temporary exclude using gist (
        vehicle_id with =, duty with =, daterange(from_date, to_date, '[]') with &&) where (temporary)
);
create index vehicle_assignment_vehicle_idx on vehicle_assignment (vehicle_id, from_date);
create index vehicle_assignment_staff_idx   on vehicle_assignment (staff_id, from_date);

create table route (
    id                   bigint generated always as identity primary key,
    name                 varchar(80) not null unique,       -- 'Route 4'
    vehicle_id           bigint references vehicle (id),
    school_arrival_time  time        not null default '08:10',
    school_leave_time    time        not null default '14:40',
    active               boolean     not null default true,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);
-- One vehicle runs at most one active route.
create unique index route_one_per_vehicle on route (vehicle_id) where active;

-- Stops in the morning order. The evening goes the other way round.
-- A removed stop is set active = false (old bus rows still point at it).
create table route_stop (
    id            bigint generated always as identity primary key,
    route_id      bigint      not null references route (id),
    seq_no        int         not null check (seq_no > 0),
    name          varchar(80) not null,                    -- 'Jakhal'
    name_hindi    varchar(80),                             -- 'जाखल', for the attendant app
    morning_time  time,
    evening_time  time,
    active        boolean     not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    -- Lets transport_enrolment check "this stop is on this route" with a foreign key.
    constraint route_stop_id_route unique (id, route_id)
);
-- 1, 2, 3 with no repeat among the active stops of a route.
-- To reorder, the service first sets seq_no = seq_no + 1000 on all stops of the route, then writes the new order.
create unique index route_stop_seq on route_stop (route_id, seq_no) where active;

-- The login of an attendant points at their staff row. One login per person.
alter table app_user add constraint app_user_staff_fk foreign key (staff_id) references staff (id);
create unique index app_user_one_per_staff on app_user (staff_id) where staff_id is not null;
