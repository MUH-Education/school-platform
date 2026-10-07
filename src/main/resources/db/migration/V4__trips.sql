-- Phase 4: one tap of the bus attendant.
-- Example: Aryan, Route 4, 7 Oct 2026, BOARDED_MORNING, DONE, 07:42:10, by Balwan.
create table boarding_event (
    id           bigint generated always as identity primary key,
    student_id   bigint      not null references student (id),
    route_id     bigint      not null references route (id),
    service_date date        not null,
    event_type   varchar(20) not null,
    outcome      varchar(20) not null,
    occurred_at  timestamptz not null,
    recorded_by  bigint      not null references app_user (id),
    recorded_at  timestamptz not null default now(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    -- The most important constraint of the system: the same tap sent twice is one row.
    constraint boarding_event_uk unique (student_id, service_date, event_type),
    constraint boarding_event_type_ck check (event_type in ('BOARDED_MORNING', 'REACHED_SCHOOL', 'BOARDED_EVENING',
        'REACHED_HOME')),
    constraint boarding_event_outcome_ck check (outcome in ('DONE', 'ABSENT', 'NOT_TRAVELLING')),
    -- "Went home with a parent" only makes sense for the evening bus.
    constraint boarding_event_not_travelling_ck check (outcome <> 'NOT_TRAVELLING' or event_type = 'BOARDED_EVENING')
);

create index boarding_event_date_route_ix on boarding_event (service_date, route_id);
create index boarding_event_recorded_by_ix on boarding_event (recorded_by);
create index boarding_event_route_ix on boarding_event (route_id);
