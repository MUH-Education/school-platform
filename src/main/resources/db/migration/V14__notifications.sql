-- =====================================================================
-- V14  Phase 11: the Notification module (docs/10-notifications.md sections 4, 5, 10).
--   One `notification` row per send. NotificationDispatcher expands it into message_outbox rows,
--   the existing OutboxWorker sends them. Templates, buttons, the stop list and incoming replies.
--   Nothing about cost: no price, budget or paise column anywhere.
-- =====================================================================

-- ---------------------------------------------------------------------
-- message_template: stretched for notification templates.
-- The rows of V5 (bus, receipt, reminder texts) become kind SYSTEM.
-- Example: kind MARKETING, name 'Open day, October', header IMAGE, media_key 'notifications/3f2a.jpg'.
-- ---------------------------------------------------------------------
alter table message_template add column kind varchar(20) not null default 'SYSTEM'
    check (kind in ('SYSTEM', 'NOTICE', 'URGENT', 'MARKETING', 'GREETING', 'FEE_REMINDER', 'ONE_FAMILY', 'STAFF'));
-- The old rows got SYSTEM above. From now on every new row must say its kind (it never changes later).
alter table message_template alter column kind drop default;

alter table message_template add column header_type varchar(10) not null default 'NONE'
    check (header_type in ('NONE', 'IMAGE', 'VIDEO', 'DOCUMENT'));
alter table message_template add column media_key varchar(120);                 -- the sample file the template was approved with
alter table message_template add column sms_body varchar(320);                  -- the short SMS version, null = no SMS
alter table message_template add column example_values jsonb not null default '{}';   -- {"Parent name": "Rajesh Punia"}
alter table message_template add column provider_template_name varchar(60);    -- 'open_day_oct'
alter table message_template add column provider_language varchar(10) not null default 'hi';
alter table message_template add column rejection_reason varchar(300);          -- the provider's text when REJECTED
alter table message_template add column replaces_id bigint references message_template (id);  -- edit of an APPROVED row = new row
alter table message_template add column created_by bigint references app_user (id);

-- A system text is found by its code (BOARDED_MORNING + gender). A notification template is found by its id
-- and has no code. So the code is needed, and unique, only for SYSTEM rows.
alter table message_template alter column code drop not null;
alter table message_template drop constraint message_template_once;
create unique index message_template_system_code on message_template (code, gender) nulls not distinct
    where kind = 'SYSTEM';
alter table message_template add constraint message_template_system_has_code
    check (kind <> 'SYSTEM' or code is not null);
alter table message_template add constraint message_template_header_media
    check (header_type = 'NONE' or media_key is not null);
alter table message_template add constraint message_template_rejection
    check (rejection_reason is null or approval = 'REJECTED');
create index message_template_kind_idx     on message_template (kind) where active;
create index message_template_replaces_idx on message_template (replaces_id);

-- ---------------------------------------------------------------------
-- message_template_button: up to 3 buttons under a WhatsApp template.
--   REPLY → action is what the tap means: STOP_MESSAGES, BOOK_VISIT, GOT_IT, CALL_ME
--   CALL  → action is the phone to call
--   URL   → action is the link
-- A MARKETING or GREETING template always ends with REPLY STOP_MESSAGES (added by the server).
-- ---------------------------------------------------------------------
create table message_template_button (
    id           bigint generated always as identity primary key,
    template_id  bigint       not null references message_template (id),
    seq_no       int          not null check (seq_no between 1 and 3),
    type         varchar(10)  not null check (type in ('CALL', 'URL', 'REPLY')),
    text         varchar(25)  not null,                     -- WhatsApp's limit for a button
    action       varchar(300) not null,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint message_template_button_once unique (template_id, seq_no),
    constraint message_template_button_action check (
        (type = 'REPLY' and action in ('STOP_MESSAGES', 'BOOK_VISIT', 'GOT_IT', 'CALL_ME'))
        or (type = 'CALL' and action ~ '^\+91[6-9][0-9]{9}$')
        or (type = 'URL' and action ~ '^https://'))
);

-- ---------------------------------------------------------------------
-- notification: one row per send. The people are rows in message_outbox (notification_id).
-- Example: MARKETING 'Open day, Sunday 25 October', audience {"groups":["CURRENT_PARENTS"],...}, SCHEDULED.
-- The audience is the QUESTION ("families with dues, 2+ months"), never the answer: the list is made at
-- expand time, and every recipient is checked again right before sending.
-- ---------------------------------------------------------------------
create table notification (
    id                 bigint generated always as identity primary key,
    kind               varchar(20)   not null check (kind in ('NOTICE', 'URGENT', 'MARKETING', 'GREETING', 'FEE_REMINDER', 'ONE_FAMILY', 'STAFF')),
    name               varchar(80)   not null,                      -- office-only name: "Open day, Sunday 25 October"
    template_id        bigint        not null references message_template (id),
    body               varchar(1024) not null,                      -- the text with {boxes}, as the office saw it
    sms_body           varchar(320),                                -- SMS version, null = no SMS fallback
    fixed_values       jsonb         not null default '{}',         -- {Date}: "10 अक्टूबर" — same for everyone
    media_key          varchar(120),                                -- our storage key (poster, video, pdf)
    media_type         varchar(10)   check (media_type in ('IMAGE', 'VIDEO', 'DOCUMENT', 'AUDIO')),
    provider_media_id  varchar(120),                                -- filled by MediaPublisher at send time
    provider_media_at  timestamptz,                                 -- when it was uploaded; re-upload after 25 days
    audience           jsonb         not null,                      -- groups + filter
    status             varchar(12)   not null default 'DRAFT' check (status in ('DRAFT', 'SCHEDULED', 'SENDING', 'SENT', 'CANCELLED', 'FAILED')),
    scheduled_at       timestamptz,
    expanded_at        timestamptz,                                 -- when the outbox rows were written
    finished_at        timestamptz,                                 -- when the last row left QUEUED
    recipient_count    int           check (recipient_count >= 0),  -- counted at expand time
    note               varchar(300),                                -- "Cancelled while sending: 112 sent, 134 not sent"
    source             varchar(10)   not null default 'MANUAL' check (source in ('MANUAL', 'RULE')),
    rule_id            bigint,                                      -- Phase 12: notification_rule (foreign key added there)
    created_by         bigint        not null references app_user (id),
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    constraint notification_schedule check (status <> 'SCHEDULED' or scheduled_at is not null),
    constraint notification_media check ((media_key is null) = (media_type is null)),
    constraint notification_rule_source check (source = 'RULE' or rule_id is null)
);
create index notification_due_idx      on notification (scheduled_at) where status = 'SCHEDULED';
create index notification_open_idx     on notification (status) where status in ('SENDING');
create index notification_list_idx     on notification (created_at desc);
create index notification_template_idx on notification (template_id);

-- ---------------------------------------------------------------------
-- message_outbox: notification rows live here too, next to bus SMS and receipts.
-- ---------------------------------------------------------------------
alter table message_outbox add column notification_id bigint references notification (id);
alter table message_outbox add column staff_id        bigint references staff (id);      -- a STAFF recipient
alter table message_outbox add column enquiry_id      bigint references enquiry (id);    -- an ENQUIRY recipient
alter table message_outbox add column read_at         timestamptz;
alter table message_outbox add column button_action   varchar(30)
    check (button_action in ('STOP_MESSAGES', 'BOOK_VISIT', 'GOT_IT', 'CALL_ME'));       -- the last button tapped
alter table message_outbox add column media_key       varchar(120);                      -- null for text only
-- The {box} values of this one person, so the row is complete on its own and can be sent again.
-- ("values" is an SQL word, so the column is template_values.)
alter table message_outbox add column template_values jsonb not null default '{}';
-- Why a guard did not let this row go: ALREADY_PAID, STOPPED, WEEKLY_CAP, ... (enum SkipReason in Java).
-- No list here on purpose: a new guard is one Java class, not a migration.
alter table message_outbox add column skip_reason     varchar(30);

-- Same length as notification.body: WhatsApp allows 1024 letters.
alter table message_outbox alter column body type varchar(1024);

alter table message_outbox drop constraint message_outbox_purpose_check;
alter table message_outbox add constraint message_outbox_purpose_check
    check (purpose in ('BOARDING', 'FEE_RECEIPT', 'FEE_REMINDER', 'RECEIPT_CANCELLED', 'ENQUIRY', 'TEST', 'OTHER',
                       'NOTICE', 'MARKETING', 'URGENT', 'STAFF', 'ONE_FAMILY'));
alter table message_outbox drop constraint message_outbox_status_check;
alter table message_outbox add constraint message_outbox_status_check
    check (status in ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'CANCELLED', 'TEST_ONLY', 'SKIPPED'));
alter table message_outbox add constraint message_outbox_skip_reason
    check ((status = 'SKIPPED') = (skip_reason is not null));

-- One copy per phone per channel per notification. URGENT goes on WhatsApp AND SMS, so channel is part of it.
-- Not counted: an SMS that replaces a failed WhatsApp (fallback_of_id), and the test copy to the sender.
create unique index message_outbox_one_per_phone on message_outbox (notification_id, phone, channel)
    where notification_id is not null and fallback_of_id is null and purpose <> 'TEST';
create index message_outbox_notification_idx on message_outbox (notification_id, status);
create index message_outbox_staff_idx        on message_outbox (staff_id);
create index message_outbox_enquiry_idx      on message_outbox (enquiry_id);

-- ---------------------------------------------------------------------
-- message_stop: families who said "stop" to MARKETING and GREETING. They still get notices, urgent
-- messages, fee reminders, bus SMS and receipts. Taking a family off the list deletes the row.
-- Example: guardian 210, WROTE_STOP, after notification 17.
-- ---------------------------------------------------------------------
create table message_stop (
    id               bigint generated always as identity primary key,
    guardian_id      bigint       not null unique references guardian (id),
    reason           varchar(20)  not null check (reason in ('TAPPED_BUTTON', 'WROTE_STOP')),
    notification_id  bigint       references notification (id),     -- the message they answered, if known
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);
create index message_stop_notification_idx on message_stop (notification_id);

-- ---------------------------------------------------------------------
-- inbound_message: every WhatsApp message that comes IN to the school number (from the provider's webhook).
-- The inbox screen is the provider's for now; this table is for STOP, button taps, the 24-hour window,
-- and "skip families who promised to pay" (promised_until).
-- Example: +919811100001, "शुक्रवार तक जमा कर देंगे", guardian 210, handled, promised_until 2026-10-23.
-- ---------------------------------------------------------------------
create table inbound_message (
    id                     bigint generated always as identity primary key,
    -- Any country: a reply from a number abroad must be saved, not crash the webhook.
    phone                  varchar(16)   not null check (phone ~ '^\+[1-9][0-9]{6,14}$'),
    guardian_id            bigint        references guardian (id),
    staff_id               bigint        references staff (id),
    enquiry_id             bigint        references enquiry (id),
    text                   varchar(4096),                             -- null for a button tap
    button_action          varchar(30)   check (button_action in ('STOP_MESSAGES', 'BOOK_VISIT', 'GOT_IT', 'CALL_ME')),
    in_reply_to_outbox_id  bigint        references message_outbox (id),
    provider_ref           varchar(120)  not null unique,             -- the same webhook twice saves one row
    received_at            timestamptz   not null,
    handled                boolean       not null default false,
    handled_by             bigint        references app_user (id),    -- empty + handled = done by the server (STOP)
    note                   varchar(300),
    promised_until         date,                                      -- "will pay by Friday": no fee reminder until then
    created_at             timestamptz   not null default now(),
    updated_at             timestamptz   not null default now(),
    constraint inbound_message_has_content check (text is not null or button_action is not null)
);
create index inbound_message_phone_idx     on inbound_message (phone, received_at desc);
create index inbound_message_open_idx      on inbound_message (received_at desc) where not handled;
create index inbound_message_guardian_idx  on inbound_message (guardian_id);
create index inbound_message_promised_idx  on inbound_message (guardian_id, promised_until) where promised_until is not null;
create index inbound_message_reply_to_idx  on inbound_message (in_reply_to_outbox_id);

-- ---------------------------------------------------------------------
-- Settings: quiet hours (school time, HH:mm) and the weekly cap for marketing and greetings.
-- ---------------------------------------------------------------------
insert into app_setting (key, value) values
    ('notifications.quiet_from',                         '21:00'),
    ('notifications.quiet_to',                           '08:00'),
    ('notifications.max_marketing_per_family_per_week',  '2');
