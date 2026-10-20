-- =====================================================================
-- V3  Phase 3: students, parents' phone numbers, bus history,
--     documents, uploads, admission drafts, transfer certificates,
--     and the number counters (admission, receipt, TC, refund).
-- =====================================================================

-- ---------------------------------------------------------------------
-- number_counter: the last number used, for each kind and year.
-- Keys: 'ADMISSION-2026', 'RECEIPT-2026', 'TC-2026', 'REFUND-2026'.
-- The service does: select ... for update; last_no + 1; build 'R-2026-1189'.
-- So two clerks never get the same number. (Replaces admission_counter and receipt_counter.)
-- ---------------------------------------------------------------------
create table number_counter (
    counter_key  varchar(30) primary key check (counter_key ~ '^(ADMISSION|RECEIPT|TC|REFUND)-[0-9]{4}$'),
    last_no      int         not null default 0 check (last_no >= 0),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------
-- student
-- A child admitted for next year (joined_on in the future) is ACTIVE but not counted yet.
-- ---------------------------------------------------------------------
create table student (
    id                    bigint generated always as identity primary key,
    admission_no          varchar(20)  not null unique check (admission_no ~ '^A-[0-9]{4}-[0-9]{3,}$'),  -- 'A-2026-118'
    name                  varchar(120) not null,
    name_hindi            varchar(120),                     -- 'आर्यन', for the attendant app
    dob                   date         not null,
    gender                varchar(1)   not null check (gender in ('M', 'F')),   -- Hindi SMS: चढ़ गया / चढ़ गई
    class_name            varchar(10)  not null check (class_name in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    section               varchar(1)   check (section in ('A', 'B', 'C')),
    village               varchar(80)  not null,
    address               varchar(200),
    father_occupation     varchar(30)  not null check (father_occupation in ('FARMER_SMALL', 'FARMER_LARGE', 'GOVT_EMPLOYEE', 'EX_SERVICEMAN', 'SHOPKEEPER', 'PRIVATE_JOB', 'LABOUR', 'TEACHER_PROFESSIONAL', 'FAMILY_ABROAD', 'OTHER')),
    status                varchar(10)  not null default 'ACTIVE' check (status in ('ACTIVE', 'LEFT', 'FINISHED')),
    admission_session_id  bigint       references academic_session (id),  -- empty for children imported from the old register
    joined_on             date         not null,
    left_on               date,
    left_reason           varchar(30)  check (left_reason in ('MOVING_TOWN', 'OTHER_SCHOOL_HERE', 'FEES_TOO_HIGH', 'BUS_OR_DISTANCE', 'FINISHED_CLASS_12', 'OTHER')),
    left_note             varchar(300),
    new_school            varchar(120),
    photo_key             varchar(200),                      -- key in private storage, never a public URL
    photo_updated_at      timestamptz,
    created_by            bigint       not null references app_user (id),
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint student_left_has_date check (status = 'ACTIVE' or left_on is not null),
    constraint student_left_has_reason check (status <> 'LEFT' or left_reason is not null)
);
create index student_admission_session_idx on student (admission_session_id);
create index student_class_idx on student (class_name, section) where status = 'ACTIVE';
create index student_village_idx on student (village);

-- ---------------------------------------------------------------------
-- guardian: a parent or relative, with their phone number.
-- One phone = one row, even if it belongs to three children.
-- A parent can be saved with no phone (receipts show "Mother, no number saved").
-- ---------------------------------------------------------------------
create table guardian (
    id                  bigint generated always as identity primary key,
    name                varchar(120),
    phone               varchar(13) unique check (phone ~ '^\+91[6-9][0-9]{9}$'),
    preferred_language  varchar(5)  not null default 'hi',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint guardian_name_or_phone check (name is not null or phone is not null)
);

-- This phone belongs to this child.
-- The PRIMARY guardian is the "family": fees, receipts and reminders go to the family.
-- Example: Rajesh Punia is primary for Aryan and Siya, so they are one family (familyId = 210).
create table student_guardian (
    id           bigint generated always as identity primary key,
    student_id   bigint      not null references student (id),
    guardian_id  bigint      not null references guardian (id),
    relation     varchar(20) not null check (relation in ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'UNCLE_AUNT', 'OTHER')),
    sms_enabled  boolean     not null default true,      -- "Send bus SMS to this number"
    is_primary   boolean     not null default false,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint student_guardian_once unique (student_id, guardian_id)
);
create index student_guardian_guardian_idx on student_guardian (guardian_id);
-- Exactly one primary per child is checked in the service; at most one is checked here.
create unique index student_guardian_one_primary on student_guardian (student_id) where is_primary;

-- ---------------------------------------------------------------------
-- transport_enrolment: the child's bus history, as periods that never overlap.
-- Every change is a new row, and the row before it gets a to_date.
--   uses_bus = true  → route, stop and bus_fee_per_month are filled.
--   uses_bus = false → "no bus" for these days (route and stop are empty).
-- The id of the new row is the "changeId" of the API. Undo = delete it and open the row before again.
-- Example: Ishaan, no bus from 1 Apr 2026 (row 1). Route 9 from 2 Nov (row 2). Row 1 gets to_date 1 Nov.
-- ---------------------------------------------------------------------
create table transport_enrolment (
    id                 bigint generated always as identity primary key,
    student_id         bigint        not null references student (id),
    uses_bus           boolean       not null,
    route_id           bigint        references route (id),
    stop_id            bigint,
    from_date          date          not null,
    to_date            date,                                   -- empty = still going on
    bus_fee_per_month  numeric(12,2) check (bus_fee_per_month >= 0),
    reason             varchar(20)   check (reason in ('ADMISSION', 'FAMILY_WILL_DROP', 'MOVING_HOUSE', 'SHORTER_DISTANCE', 'OWNER_DECISION', 'LEFT_SCHOOL', 'IMPORT', 'OTHER')),
    note               varchar(200),
    created_by         bigint        not null references app_user (id),
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    constraint transport_enrolment_dates check (to_date is null or to_date >= from_date),
    constraint transport_enrolment_bus_fields check (
        (uses_bus and route_id is not null and stop_id is not null and bus_fee_per_month is not null)
        or (not uses_bus and route_id is null and stop_id is null and bus_fee_per_month is null)),
    -- The stop must be a stop of the same route.
    constraint transport_enrolment_stop_on_route foreign key (stop_id, route_id) references route_stop (id, route_id),
    -- A child has one bus state on any day.
    constraint transport_enrolment_no_overlap exclude using gist (
        student_id with =, daterange(from_date, to_date, '[]') with &&)
);
create index transport_enrolment_student_idx on transport_enrolment (student_id, from_date desc);
create index transport_enrolment_route_idx   on transport_enrolment (route_id, from_date) where uses_bus;
create index transport_enrolment_stop_idx    on transport_enrolment (stop_id);

-- ---------------------------------------------------------------------
-- upload: a file chosen on the admission form before the student exists.
-- Deleted by a nightly job when expires_at has passed and used_at is empty.
-- ---------------------------------------------------------------------
create table upload (
    id            bigint generated always as identity primary key,
    public_id     varchar(40)  not null unique,                 -- 'upl_7f3a9c2e', the uploadId of the API
    file_key      varchar(200) not null,                        -- key in private storage
    file_name     varchar(200) not null,
    content_type  varchar(40)  not null check (content_type in ('application/pdf', 'image/jpeg', 'image/png')),
    size_bytes    int          not null check (size_bytes between 1 and 5242880),   -- up to 5 MB
    uploaded_by   bigint       not null references app_user (id),
    expires_at    timestamptz  not null,                        -- uploaded + 24 hours
    used_at       timestamptz,                                  -- set when POST /admissions uses it
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

-- ---------------------------------------------------------------------
-- student_document: one file of one child (birth certificate, TC, ...).
-- A new file for the same type replaces the old one: the old row gets removed_at, and is kept.
-- ---------------------------------------------------------------------
create table student_document (
    id                    bigint generated always as identity primary key,
    student_id            bigint       not null references student (id),
    doc_type              varchar(30)  not null check (doc_type in ('BIRTH_CERTIFICATE', 'PARENT_ID', 'ADDRESS_PROOF', 'TRANSFER_CERTIFICATE', 'LAST_REPORT_CARD', 'CHILD_AADHAAR', 'OTHER')),
    title                 varchar(80),                          -- only for OTHER: 'Medical certificate'
    file_key              varchar(200) not null,
    file_name             varchar(200) not null,
    content_type          varchar(40)  not null check (content_type in ('application/pdf', 'image/jpeg', 'image/png')),
    size_bytes            int          not null check (size_bytes between 1 and 5242880),
    original_kept         boolean      not null default false,   -- the school keeps the paper original
    original_returned_on  date,                                 -- given back when the child leaves
    added_by              bigint       not null references app_user (id),
    removed_at            timestamptz,
    removed_by            bigint       references app_user (id),
    removed_reason        varchar(10)  check (removed_reason in ('REMOVED', 'REPLACED')),
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint student_document_other_has_title check (doc_type <> 'OTHER' or title is not null),
    constraint student_document_removed check ((removed_at is null) = (removed_reason is null))
);
create index student_document_student_idx on student_document (student_id);
-- One shown file per type (OTHER can have many).
create unique index student_document_one_per_type on student_document (student_id, doc_type)
    where removed_at is null and doc_type <> 'OTHER';

-- A note on a document type, even when no file is added yet: 'The old school has not given it yet'.
create table student_document_note (
    id          bigint generated always as identity primary key,
    student_id  bigint       not null references student (id),
    doc_type    varchar(30)  not null check (doc_type in ('BIRTH_CERTIFICATE', 'PARENT_ID', 'ADDRESS_PROOF', 'TRANSFER_CERTIFICATE', 'LAST_REPORT_CARD', 'CHILD_AADHAAR', 'OTHER')),
    note        varchar(200) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint student_document_note_once unique (student_id, doc_type)
);

-- ---------------------------------------------------------------------
-- admission_draft: the admission form as the React app keeps it ("Save as draft").
-- Nothing in it is checked. Deleted after 60 days, or when the admission is saved.
-- ---------------------------------------------------------------------
create table admission_draft (
    id          bigint generated always as identity primary key,
    title       varchar(120) not null,                -- 'Kavya Goyal, UKG'
    session_id  bigint       not null references academic_session (id),
    form        jsonb        not null,
    created_by  bigint       not null references app_user (id),
    updated_by  bigint       not null references app_user (id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create index admission_draft_session_idx on admission_draft (session_id);

-- ---------------------------------------------------------------------
-- transfer_certificate: TC-2026-014. Only one ISSUED TC per child.
-- ---------------------------------------------------------------------
create table transfer_certificate (
    id            bigint generated always as identity primary key,
    tc_no         varchar(20)  not null unique check (tc_no ~ '^TC-[0-9]{4}-[0-9]{3,}$'),
    student_id    bigint       not null references student (id),
    issued_on     date         not null,
    conduct       varchar(20)  not null check (conduct in ('VERY_GOOD', 'GOOD', 'SATISFACTORY')),
    class_passed  varchar(80),                          -- 'Class 8, in March 2026'
    remarks       varchar(200),
    status        varchar(10)  not null default 'ISSUED' check (status in ('ISSUED', 'CANCELLED')),
    made_by       bigint       not null references app_user (id),
    cancelled_at  timestamptz,
    cancelled_by  bigint       references app_user (id),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint transfer_certificate_cancelled check ((status = 'CANCELLED') = (cancelled_at is not null))
);
create index transfer_certificate_student_idx on transfer_certificate (student_id);
create unique index transfer_certificate_one_issued on transfer_certificate (student_id) where status = 'ISSUED';
