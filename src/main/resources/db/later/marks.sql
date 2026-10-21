-- =====================================================================
-- LATER, not in the first release: exam marks (Student file: marks).
--     When you build marks, copy this file to db/migration with the next free number,
--     for example V9__marks.sql. Until then it must NOT be in db/migration.
--
-- exam            the 4 exams of a session: UT1 (July, /20), HY (September, /80), UT2, FINAL
-- subject         English, Hindi, Maths, EVS, Computer, Drawing (grade only)
-- class_subject   which subjects a class has
-- student_mark    one mark (or grade) of one child, one exam, one subject
-- teacher_note    the class teacher's note for one child after one exam
-- =====================================================================

create table exam (
    id               bigint generated always as identity primary key,
    session_id       bigint      not null references academic_session (id),
    code             varchar(10) not null check (code in ('UT1', 'HY', 'UT2', 'FINAL')),
    name             varchar(40) not null,              -- 'Half-yearly'
    month_label      varchar(20) not null,              -- 'September'
    max_per_subject  int         not null check (max_per_subject > 0),
    seq_no           int         not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint exam_once unique (session_id, code)
);

create table subject (
    id          bigint generated always as identity primary key,
    name        varchar(40) not null unique,            -- 'Maths'
    grade_only  boolean     not null default false,     -- Drawing: a grade, not marks; not in the total
    seq_no      int         not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table class_subject (
    id          bigint generated always as identity primary key,
    class_name  varchar(10) not null check (class_name in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    subject_id  bigint      not null references subject (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint class_subject_once unique (class_name, subject_id)
);
create index class_subject_subject_idx on class_subject (subject_id);

create table student_mark (
    id          bigint generated always as identity primary key,
    student_id  bigint       not null references student (id),
    exam_id     bigint       not null references exam (id),
    subject_id  bigint       not null references subject (id),
    marks       numeric(5,1) check (marks >= 0),        -- 68.0
    grade       varchar(3),                             -- 'A' for a grade-only subject
    entered_by  bigint       not null references app_user (id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint student_mark_once unique (student_id, exam_id, subject_id),
    constraint student_mark_one_value check ((marks is null) <> (grade is null))
);
create index student_mark_exam_idx    on student_mark (exam_id);
create index student_mark_subject_idx on student_mark (subject_id);

create table teacher_note (
    id               bigint generated always as identity primary key,
    student_id       bigint       not null references student (id),
    exam_id          bigint       not null references exam (id),
    note             varchar(500) not null,
    written_by_name  varchar(120) not null,             -- 'Ms Rekha' (teachers do not log in yet)
    written_on       date         not null,
    created_by       bigint       not null references app_user (id),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint teacher_note_once unique (student_id, exam_id)
);
create index teacher_note_exam_idx on teacher_note (exam_id);
