-- =====================================================================
-- V4  Phase 4: the attendant's taps, and school holidays.
-- =====================================================================

-- One tap: one child, one school day, one of the four events.
-- A CLEARED tap (undo) deletes the row.
create table boarding_event (
    id            bigint generated always as identity primary key,
    student_id    bigint      not null references student (id),
    route_id      bigint      not null references route (id),       -- the child's route on that day
    service_date  date        not null,                             -- the school day, Asia/Kolkata
    event_type    varchar(20) not null check (event_type in ('BOARDED_MORNING', 'REACHED_SCHOOL', 'BOARDED_EVENING', 'REACHED_HOME')),
    outcome       varchar(20) not null check (outcome in ('DONE', 'ABSENT', 'NOT_TRAVELLING')),
    occurred_at   timestamptz not null,                             -- time of the tap ON THE PHONE
    recorded_by   bigint      not null references app_user (id),
    recorded_at   timestamptz not null default now(),               -- time the server got it
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    -- THE most important rule: a double tap, or a tap sent again after bad signal, hits the same row.
    -- Save with: insert ... on conflict (student_id, service_date, event_type) do update ...
    --            where boarding_event.occurred_at <= excluded.occurred_at   (the later tap wins)
    constraint boarding_event_one_per_event unique (student_id, service_date, event_type),
    -- "Went home with a parent" exists only in the evening.
    constraint boarding_event_not_travelling_evening check (outcome <> 'NOT_TRAVELLING' or event_type = 'BOARDED_EVENING')
);
create index boarding_event_day_route_idx on boarding_event (service_date, route_id);
create index boarding_event_route_idx on boarding_event (route_id, service_date);

-- Days with no school (Sundays are always off and are not stored here).
create table holiday (
    id            bigint generated always as identity primary key,
    holiday_date  date        not null unique,
    name          varchar(80) not null,                 -- 'Diwali'
    created_by    bigint      not null references app_user (id),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
