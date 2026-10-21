-- =====================================================================
-- V5  Phase 5: which bus SMS each class gets, the message texts,
--     and the queue of every message to a parent.
-- =====================================================================

-- Which of the four bus events send an SMS, for a group of classes.
-- Every class must be in exactly one group (checked in the service).
create table bus_sms_rule (
    id               bigint generated always as identity primary key,
    seq_no           int         not null unique,
    name             varchar(60) not null,                 -- 'Nursery to Class 8'
    from_class       varchar(10) not null check (from_class in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    to_class         varchar(10) not null check (to_class   in ('Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12')),
    boarded_morning  boolean     not null,
    reached_school   boolean     not null,
    boarded_evening  boolean     not null,
    reached_home     boolean     not null,
    updated_by       bigint      references app_user (id),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

insert into bus_sms_rule (seq_no, name, from_class, to_class, boarded_morning, reached_school, boarded_evening, reached_home) values
    (1, 'Nursery to Class 8', 'Nursery', '8',  true,  true,  true,  true),
    (2, 'Class 9 and 10',     '9',       '10', false, true,  true,  false),
    (3, 'Class 11 and 12',    '11',      '12', false, false, false, false);

-- A message text. Bus texts have a boy row (gender M) and a girl row (gender F).
-- Fee texts have one row (gender empty).
-- body          = the text as changed on School settings.
-- approved_body = the text approved by the phone company (DLT). The server always SENDS approved_body.
-- Words in curly brackets are filled in: {name} {time} {stop} {names} {amount} {months} {receiptNo} {currentMonth} {lateFee} {payBy}
-- The school name is written in the text, because the phone company approves the exact text.
create table message_template (
    id                    bigint generated always as identity primary key,
    code                  varchar(40)  not null,           -- 'BOARDED_MORNING'
    gender                varchar(1)   check (gender in ('M', 'F')),
    name                  varchar(60)  not null,           -- 'Boarded morning'
    channel               varchar(10)  not null check (channel in ('SMS', 'WHATSAPP')),
    language              varchar(5)   not null default 'hi',
    body                  varchar(1000) not null,
    approved_body         varchar(1000),
    provider_template_id  varchar(60),
    approval              varchar(10)  not null default 'WAITING' check (approval in ('APPROVED', 'WAITING', 'REJECTED')),
    active                boolean      not null default true,
    updated_by            bigint       references app_user (id),
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    constraint message_template_once unique nulls not distinct (code, gender)
);

insert into message_template (code, gender, name, channel, body, approved_body, approval) values
    ('BOARDED_MORNING', 'M', 'Boarded morning',   'SMS', '{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School',  '{name} सुबह की बस में चढ़ गया — {time}। MUH Jain School',  'WAITING'),
    ('BOARDED_MORNING', 'F', 'Boarded morning',   'SMS', '{name} सुबह की बस में चढ़ गई — {time}। MUH Jain School',   '{name} सुबह की बस में चढ़ गई — {time}। MUH Jain School',   'WAITING'),
    ('REACHED_SCHOOL',  'M', 'Reached school',    'SMS', '{name} स्कूल पहुँच गया — {time}। MUH Jain School',         '{name} स्कूल पहुँच गया — {time}। MUH Jain School',         'WAITING'),
    ('REACHED_SCHOOL',  'F', 'Reached school',    'SMS', '{name} स्कूल पहुँच गई — {time}। MUH Jain School',          '{name} स्कूल पहुँच गई — {time}। MUH Jain School',          'WAITING'),
    ('BOARDED_EVENING', 'M', 'Boarded evening',   'SMS', '{name} छुट्टी की बस में चढ़ गया — {time}। MUH Jain School', '{name} छुट्टी की बस में चढ़ गया — {time}। MUH Jain School', 'WAITING'),
    ('BOARDED_EVENING', 'F', 'Boarded evening',   'SMS', '{name} छुट्टी की बस में चढ़ गई — {time}। MUH Jain School',  '{name} छुट्टी की बस में चढ़ गई — {time}। MUH Jain School',  'WAITING'),
    ('REACHED_HOME',    'M', 'Reached home stop', 'SMS', '{name} अपने स्टॉप पर उतर गया — {time}। MUH Jain School',   '{name} अपने स्टॉप पर उतर गया — {time}। MUH Jain School',   'WAITING'),
    ('REACHED_HOME',    'F', 'Reached home stop', 'SMS', '{name} अपने स्टॉप पर उतर गई — {time}। MUH Jain School',    '{name} अपने स्टॉप पर उतर गई — {time}। MUH Jain School',    'WAITING'),
    ('FEE_RECEIPT',     null, 'Fee receipt',      'WHATSAPP', '{names} की फ़ीस {amount} मिल गई है ({months})। रसीद नंबर {receiptNo} साथ में भेजी है। धन्यवाद — MUH Jain Global School', '{names} की फ़ीस {amount} मिल गई है ({months})। रसीद नंबर {receiptNo} साथ में भेजी है। धन्यवाद — MUH Jain Global School', 'WAITING'),
    ('FEE_RECEIPT_SMS', null, 'Fee receipt (SMS when WhatsApp fails)', 'SMS', '{names}: फ़ीस {amount} मिली। रसीद {receiptNo}। – MUH Jain School', '{names}: फ़ीस {amount} मिली। रसीद {receiptNo}। – MUH Jain School', 'WAITING'),
    ('FEE_REMINDER',    null, 'Fee reminder',     'WHATSAPP', 'नमस्ते। {names} की {months} की फ़ीस बाकी है। {currentMonth} मिलाकर कुल {amount} (लेट फ़ीस {lateFee} सहित)। कृपया {payBy} तक जमा करें। — MUH Jain Global School', 'नमस्ते। {names} की {months} की फ़ीस बाकी है। {currentMonth} मिलाकर कुल {amount} (लेट फ़ीस {lateFee} सहित)। कृपया {payBy} तक जमा करें। — MUH Jain Global School', 'WAITING'),
    ('FEE_REMINDER_SMS', null, 'Fee reminder (SMS when WhatsApp fails)', 'SMS', '{names} की फ़ीस {amount} बाकी है। {payBy} तक जमा करें। – MUH Jain School', '{names} की फ़ीस {amount} बाकी है। {payBy} तक जमा करें। – MUH Jain School', 'WAITING'),
    ('RECEIPT_CANCELLED', null, 'Receipt cancelled', 'WHATSAPP', 'रसीद {receiptNo} ({amount}) रद्द कर दी गई है। सही रसीद जल्दी भेजेंगे। — MUH Jain Global School', null, 'WAITING');

-- ---------------------------------------------------------------------
-- message_outbox: every message to a parent. The sending job reads QUEUED rows every 5 seconds.
-- Login codes are NOT stored here (they are sent at once and kept as a hash in otp_code).
-- ---------------------------------------------------------------------
create table message_outbox (
    id              bigint generated always as identity primary key,
    purpose         varchar(20)   not null check (purpose in ('BOARDING', 'FEE_RECEIPT', 'FEE_REMINDER', 'RECEIPT_CANCELLED', 'ENQUIRY', 'TEST', 'OTHER')),
    channel         varchar(10)   not null check (channel in ('SMS', 'WHATSAPP')),
    phone           varchar(13)   not null check (phone ~ '^\+91[6-9][0-9]{9}$'),
    guardian_id     bigint        references guardian (id),
    student_id      bigint        references student (id),      -- empty for a family message (receipt for two children)
    receipt_id      bigint,                                      -- foreign key added in V7
    service_date    date,                                        -- bus messages
    event_type      varchar(20)   check (event_type in ('BOARDED_MORNING', 'REACHED_SCHOOL', 'BOARDED_EVENING', 'REACHED_HOME')),
    template_code   varchar(40),
    body            varchar(1000) not null,                      -- the final text
    status          varchar(12)   not null default 'QUEUED' check (status in ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'CANCELLED', 'TEST_ONLY')),
    attempts        int           not null default 0 check (attempts between 0 and 10),
    next_try_at     timestamptz   not null default now(),
    provider_ref    varchar(120),                                -- id from the SMS / WhatsApp company
    problem         varchar(20)   check (problem in ('PHONE_OFF', 'WRONG_NUMBER', 'NOT_ON_WHATSAPP', 'DND', 'PROVIDER_ERROR', 'OTHER')),
    error           varchar(300),
    sent_at         timestamptz,
    delivered_at    timestamptz,
    fallback_of_id  bigint        references message_outbox (id), -- this SMS went because that WhatsApp failed
    created_by      bigint        references app_user (id),       -- empty = made by the system (a tap)
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    constraint message_outbox_bus_fields check (purpose <> 'BOARDING' or (student_id is not null and service_date is not null and event_type is not null))
);
-- One bus event gives one SMS per phone, whatever happens.
create unique index message_outbox_one_bus_sms on message_outbox (guardian_id, student_id, service_date, event_type)
    where purpose = 'BOARDING' and fallback_of_id is null;
create index message_outbox_queue_idx    on message_outbox (next_try_at) where status = 'QUEUED';
create index message_outbox_day_idx      on message_outbox (created_at desc);
create index message_outbox_provider_idx on message_outbox (provider_ref) where provider_ref is not null;
create index message_outbox_student_idx  on message_outbox (student_id);
create index message_outbox_guardian_idx on message_outbox (guardian_id, purpose, created_at desc);
create index message_outbox_receipt_idx  on message_outbox (receipt_id);
create index message_outbox_fallback_idx on message_outbox (fallback_of_id);
