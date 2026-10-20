-- =====================================================================
-- V6  Phase 6: parents who asked about admission, their stages,
--     and every call or visit.
-- =====================================================================

create table enquiry (
    id                       bigint generated always as identity primary key,
    session_id               bigint        not null references academic_session (id),   -- the year the child wants to join
    parent_name              varchar(120)  not null,
    phone                    varchar(13)   not null check (phone ~ '^\+91[6-9][0-9]{9}$'),
    relation                 varchar(20)   check (relation in ('FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'UNCLE_AUNT', 'OTHER')),
    village                  varchar(80)   not null,
    child_name               varchar(120),
    class_sought             varchar(10)   not null check (class_sought in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    child_age                varchar(20),                                    -- free text: '6 years'
    current_school           varchar(120),
    source                   varchar(20)   not null check (source in ('WALK_IN', 'REFERRAL', 'FACEBOOK', 'WHATSAPP', 'HOARDING', 'BUS_ENQUIRY')),
    referred_by              varchar(120),                                   -- name typed by the office
    referred_by_guardian_id  bigint        references guardian (id),         -- a known parent: gets the referral credit
    needs_bus                varchar(10)   not null default 'UNKNOWN' check (needs_bus in ('YES', 'NO', 'UNKNOWN')),
    needs_bus_from           varchar(80),                                    -- 'Model Town'
    status                   varchar(12)   not null default 'NEW' check (status in ('NEW', 'CONTACTED', 'VISITED', 'APPLIED', 'ADMITTED', 'LOST')),
    status_before_lost       varchar(12)   check (status_before_lost in ('NEW', 'CONTACTED', 'VISITED', 'APPLIED')),  -- for "Open the enquiry again"
    lost_reason              varchar(30)   check (lost_reason in ('FEE_TOO_HIGH', 'CHOSE_OTHER_SCHOOL', 'TOO_FAR_NO_BUS', 'NO_SEAT', 'NOT_REACHABLE', 'OTHER')),
    lost_note                varchar(300),
    next_follow_up_on        date,
    next_follow_up_type      varchar(10)   check (next_follow_up_type in ('CALL', 'VISIT', 'DOCUMENTS')),
    note                     varchar(1000),
    admitted_student_id      bigint        unique references student (id),
    created_by               bigint        not null references app_user (id),
    created_at               timestamptz   not null default now(),
    updated_at               timestamptz   not null default now(),
    constraint enquiry_lost_has_reason check (status <> 'LOST' or (lost_reason is not null and status_before_lost is not null)),
    constraint enquiry_admitted_has_student check ((status = 'ADMITTED') = (admitted_student_id is not null))
);
create index enquiry_session_status_idx on enquiry (session_id, status);
create index enquiry_phone_idx on enquiry (phone);
create index enquiry_follow_up_idx on enquiry (next_follow_up_on) where status not in ('ADMITTED', 'LOST');
create index enquiry_referred_by_idx on enquiry (referred_by_guardian_id);
-- "Overdue" is counted, not stored: next_follow_up_on < today, and status is not ADMITTED or LOST.

-- Each stage reached, with its date. For the stage line "New 29 Sep → Contacted 2 Oct → ...".
create table enquiry_stage (
    id          bigint generated always as identity primary key,
    enquiry_id  bigint      not null references enquiry (id),
    status      varchar(12) not null check (status in ('NEW', 'CONTACTED', 'VISITED', 'APPLIED', 'ADMITTED', 'LOST')),
    reached_on  date        not null,
    created_by  bigint      not null references app_user (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index enquiry_stage_enquiry_idx on enquiry_stage (enquiry_id, reached_on);

-- One call, visit or WhatsApp chat, and the next step that was promised.
create table enquiry_follow_up (
    id           bigint generated always as identity primary key,
    enquiry_id   bigint        not null references enquiry (id),
    happened_on  date          not null,
    follow_type  varchar(10)   not null check (follow_type in ('CALL', 'VISIT', 'WALK_IN', 'WHATSAPP')),
    note         varchar(1000) not null,
    next_type    varchar(10)   check (next_type in ('CALL', 'VISIT', 'DOCUMENTS')),
    next_on      date,
    created_by   bigint        not null references app_user (id),
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now()
);
create index enquiry_follow_up_enquiry_idx on enquiry_follow_up (enquiry_id, happened_on desc);
