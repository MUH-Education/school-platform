-- Phase 3: students, parents' phone numbers, bus history, admission numbers.

-- One child. Never deleted: status LEFT with left_on.
-- Example: A-2026-118, Aryan, class 3, section B, village Jakhal.
create table student (
    id                bigint generated always as identity primary key,
    admission_no      varchar(20)  not null,
    name              varchar(120) not null,
    dob               date         not null,
    gender            char(1)      not null,
    class_name        varchar(10)  not null,
    section           varchar(4),
    village           varchar(80)  not null,
    address           varchar(200),
    father_occupation varchar(30)  not null,
    status            varchar(10)  not null default 'ACTIVE',
    joined_on         date         not null,
    left_on           date,
    photo_key         varchar(200),
    created_by        bigint references app_user (id),
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint student_admission_no_uk unique (admission_no),
    constraint student_gender_ck check (gender in ('M', 'F')),
    constraint student_class_ck check (class_name in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', '10', '11', '12')),
    constraint student_occupation_ck check (father_occupation in ('FARMER_SMALL', 'FARMER_LARGE', 'GOVT_EMPLOYEE',
        'EX_SERVICEMAN', 'SHOPKEEPER', 'PRIVATE_JOB', 'LABOUR', 'TEACHER_PROFESSIONAL', 'FAMILY_ABROAD', 'OTHER')),
    constraint student_status_ck check (status in ('ACTIVE', 'LEFT')),
    constraint student_left_ck check ((status = 'LEFT') = (left_on is not null))
);

create index student_status_class_ix on student (status, class_name);
create index student_village_ix on student (village);
create index student_name_ix on student (lower(name));
create index student_created_by_ix on student (created_by);

-- One phone number of a parent or relative. One phone = one row, even if it belongs to three children.
create table guardian (
    id                 bigint generated always as identity primary key,
    name               varchar(120),
    phone              varchar(13) not null,
    preferred_language varchar(5)  not null default 'hi',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint guardian_phone_uk unique (phone)
);

-- "This phone belongs to this child."
-- Example: Aryan and Siya share their mother's phone: 1 guardian row, 2 student_guardian rows.
create table student_guardian (
    id          bigint generated always as identity primary key,
    student_id  bigint      not null references student (id),
    guardian_id bigint      not null references guardian (id),
    relation    varchar(20) not null,
    sms_enabled boolean     not null default true,
    is_primary  boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint student_guardian_uk unique (student_id, guardian_id),
    constraint student_guardian_relation_ck check (relation in ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER',
        'UNCLE_AUNT', 'OTHER'))
);

create index student_guardian_guardian_ix on student_guardian (guardian_id);

-- A child's bus history. to_date null = still going on. No row = no bus.
-- Example: Ishaan, Route 9, Model Town stop, from 2 Nov 2026.
create table transport_enrolment (
    id         bigint generated always as identity primary key,
    student_id bigint      not null references student (id),
    route_id   bigint      not null references route (id),
    stop_id    bigint      not null references route_stop (id),
    from_date  date        not null,
    to_date    date,
    bus_fee    numeric(12, 2),
    created_by bigint references app_user (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint transport_enrolment_dates_ck check (to_date is null or to_date >= from_date)
);

-- A child has one open row only.
create unique index transport_enrolment_open_uk on transport_enrolment (student_id) where to_date is null;
create index transport_enrolment_student_ix on transport_enrolment (student_id, from_date);
-- "Children on route R on day D".
create index transport_enrolment_route_ix on transport_enrolment (route_id, from_date);
create index transport_enrolment_stop_ix on transport_enrolment (stop_id);
create index transport_enrolment_created_by_ix on transport_enrolment (created_by);

-- One row per year. The server adds 1 and builds A-2026-119. Example: year 2026, last_no 118.
create table admission_counter (
    year       int primary key,
    last_no    int         not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
