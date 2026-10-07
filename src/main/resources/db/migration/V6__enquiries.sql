-- Phase 6: admission enquiries and the calls and visits that follow them.

-- One parent asking about admission. Example: Ramesh Jain from Jakhal asks about Class 3, source WALK_IN, status NEW.
create table enquiry (
    id                      bigint generated always as identity primary key,
    parent_name             varchar(120)  not null,
    phone                   varchar(13)   not null,
    relation                varchar(20),
    village                 varchar(80)   not null,
    child_name              varchar(120),
    class_sought            varchar(10)   not null,
    child_age               varchar(20),
    current_school          varchar(120),
    source                  varchar(20)   not null,
    referred_by             varchar(120),
    referred_by_guardian_id bigint references guardian (id),
    needs_bus               varchar(10)   not null default 'UNKNOWN',
    status                  varchar(12)   not null default 'NEW',
    lost_reason             varchar(200),
    next_follow_up_on       date,
    note                    varchar(1000),
    session_name            varchar(9)    not null,
    admitted_student_id     bigint references student (id),
    created_by              bigint references app_user (id),
    created_at              timestamptz   not null default now(),
    updated_at              timestamptz   not null default now(),
    constraint enquiry_source_ck check (source in ('WALK_IN', 'REFERRAL', 'FACEBOOK', 'WHATSAPP', 'HOARDING',
        'BUS_ENQUIRY')),
    constraint enquiry_status_ck check (status in ('NEW', 'CONTACTED', 'VISITED', 'APPLIED', 'ADMITTED', 'LOST')),
    constraint enquiry_needs_bus_ck check (needs_bus in ('YES', 'NO', 'UNKNOWN')),
    constraint enquiry_class_ck check (class_sought in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', '10', '11', '12')),
    constraint enquiry_lost_reason_ck check (status <> 'LOST' or lost_reason is not null),
    constraint enquiry_admitted_ck check ((status = 'ADMITTED') = (admitted_student_id is not null))
);

-- One open enquiry for the same phone and class. Two clerks saving the same call at the same moment: one wins.
create unique index enquiry_open_phone_class_uk on enquiry (phone, class_sought)
    where status not in ('ADMITTED', 'LOST');
create index enquiry_status_ix on enquiry (status);
create index enquiry_village_ix on enquiry (village);
create index enquiry_follow_up_ix on enquiry (next_follow_up_on) where status not in ('ADMITTED', 'LOST');
create index enquiry_phone_ix on enquiry (phone);
create index enquiry_guardian_ix on enquiry (referred_by_guardian_id);
create index enquiry_student_ix on enquiry (admitted_student_id);
create index enquiry_created_by_ix on enquiry (created_by);

-- One call or visit. Example: "Called, will visit on Sunday", next action 12 Oct.
create table enquiry_follow_up (
    id             bigint generated always as identity primary key,
    enquiry_id     bigint        not null references enquiry (id),
    note           varchar(1000) not null,
    next_action_on date,
    created_by     bigint references app_user (id),
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now()
);

create index enquiry_follow_up_enquiry_ix on enquiry_follow_up (enquiry_id, id);
create index enquiry_follow_up_created_by_ix on enquiry_follow_up (created_by);
