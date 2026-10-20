-- =====================================================================
-- V8  The new school year: the 4-step move up, done once in March.
--     (Phase 8 analytics and Phase 9 go-live need no new tables.)
--
-- promotion_run     one per new year: 2026-27 → 2027-28, its status and undo time
-- promotion_choice  only the children who do NOT simply move up one class:
--                   STAY_BACK, NOT_COMING_BACK, or MOVE_UP to another section.
--                   Everyone without a row moves up and keeps section, route and stop.
--                   Class 12 without a row becomes FINISHED.
-- =====================================================================

create table promotion_run (
    id               bigint generated always as identity primary key,
    from_session_id  bigint      not null references academic_session (id),
    to_session_id    bigint      not null unique references academic_session (id),
    status           varchar(10) not null default 'DRAFT' check (status in ('DRAFT', 'STARTED', 'DONE')),
    steps_done       int         not null default 0 check (steps_done between 0 and 4),
    started_at       timestamptz,
    started_by       bigint      references app_user (id),
    undo_until       timestamptz,                       -- midnight of the start day
    undone_at        timestamptz,                       -- the last undo, if any
    undone_by        bigint      references app_user (id),
    created_by       bigint      not null references app_user (id),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint promotion_run_two_sessions check (from_session_id <> to_session_id),
    constraint promotion_run_started check (status = 'DRAFT' or (started_at is not null and started_by is not null))
);
create index promotion_run_from_idx on promotion_run (from_session_id);

create table promotion_choice (
    id            bigint      generated always as identity primary key,
    run_id        bigint      not null references promotion_run (id),
    student_id    bigint      not null references student (id),
    choice        varchar(20) not null check (choice in ('MOVE_UP', 'STAY_BACK', 'NOT_COMING_BACK')),
    to_class      varchar(10) check (to_class in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    to_section    varchar(1)  check (to_section in ('A', 'B', 'C')),
    reason        varchar(30) check (reason in ('MOVING_TOWN', 'OTHER_SCHOOL_HERE', 'FEES_TOO_HIGH', 'BUS_OR_DISTANCE', 'FINISHED_CLASS_12', 'OTHER')),
    note          varchar(200),                         -- 'Family moving to Hisar'
    -- Filled when the year is started, so "Undo" can put every child back:
    from_class    varchar(10),
    from_section  varchar(1),
    marked_by     bigint      not null references app_user (id),
    marked_at     timestamptz not null default now(),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint promotion_choice_once unique (run_id, student_id),
    constraint promotion_choice_not_coming check (choice <> 'NOT_COMING_BACK' or (reason is not null and to_class is null)),
    constraint promotion_choice_has_class check (choice = 'NOT_COMING_BACK' or to_class is not null)
);
create index promotion_choice_student_idx on promotion_choice (student_id);
