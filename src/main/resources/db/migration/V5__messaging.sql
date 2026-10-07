-- Phase 5: the texts of the parent SMS and the queue of messages to send.

-- One text. The provider approves each text on DLT, so the texts live here and not in Java code.
-- Example: BOARDED_MORNING_M, SMS, hi, "{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School".
create table message_template (
    code                 varchar(40)  primary key,
    channel              varchar(10)  not null,
    language             varchar(5)   not null,
    body                 varchar(500) not null,
    provider_template_id varchar(60),
    active               boolean      not null default true,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    constraint message_template_channel_ck check (channel in ('SMS', 'WHATSAPP'))
);

insert into message_template (code, channel, language, body) values
    ('BOARDED_MORNING_M', 'SMS', 'hi', '{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School'),
    ('BOARDED_MORNING_F', 'SMS', 'hi', '{name} सुबह की बस में चढ़ गई — {time}। MUH Jain School'),
    ('REACHED_SCHOOL_M', 'SMS', 'hi', '{name} स्कूल पहुँच गया — {time}। MUH Jain School'),
    ('REACHED_SCHOOL_F', 'SMS', 'hi', '{name} स्कूल पहुँच गई — {time}। MUH Jain School'),
    ('BOARDED_EVENING_M', 'SMS', 'hi', '{name} छुट्टी की बस में चढ़ गया — {time}। MUH Jain School'),
    ('BOARDED_EVENING_F', 'SMS', 'hi', '{name} छुट्टी की बस में चढ़ गई — {time}। MUH Jain School'),
    ('REACHED_HOME_M', 'SMS', 'hi', '{name} अपने स्टॉप पर उतर गया — {time}। MUH Jain School'),
    ('REACHED_HOME_F', 'SMS', 'hi', '{name} अपने स्टॉप पर उतर गई — {time}। MUH Jain School');

-- Every message to a parent. One row per phone. OTP codes are never stored here.
-- Example: BOARDING, SMS, +919811100001, Aryan, 7 Oct 2026, BOARDED_MORNING, QUEUED.
create table message_outbox (
    id            bigint generated always as identity primary key,
    purpose       varchar(20)  not null,
    channel       varchar(10)  not null,
    phone         varchar(13)  not null,
    guardian_id   bigint references guardian (id),
    student_id    bigint references student (id),
    service_date  date,
    event_type    varchar(20),
    template_code varchar(40) references message_template (code),
    body          varchar(500) not null,
    status        varchar(12)  not null default 'QUEUED',
    attempts      int          not null default 0,
    provider_ref  varchar(80),
    error         varchar(300),
    sent_at       timestamptz,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint message_outbox_purpose_ck check (purpose in ('BOARDING', 'ENQUIRY', 'FEE_REMINDER', 'OTHER')),
    constraint message_outbox_channel_ck check (channel in ('SMS', 'WHATSAPP')),
    constraint message_outbox_status_ck check (status in ('QUEUED', 'SENT', 'FAILED', 'TEST_ONLY'))
);

-- One event gives one SMS per phone, whatever happens (a tap sent again, an undo and a new tap).
create unique index message_outbox_boarding_uk
    on message_outbox (guardian_id, student_id, service_date, event_type) where purpose = 'BOARDING';
-- The sending job: QUEUED rows, oldest first.
create index message_outbox_status_ix on message_outbox (status, id);
create index message_outbox_student_ix on message_outbox (student_id, service_date);
create index message_outbox_guardian_ix on message_outbox (guardian_id);
create index message_outbox_date_ix on message_outbox (service_date);
