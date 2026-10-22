-- =====================================================================
-- V15  Phase 12: automatic messages (docs/phases/phase-12-automatic-rules.md).
--   A rule only CREATES a normal notification row (source = RULE) at the right moment.
--   Audience, guard chain, expand, send and report stay Phase 11.
--   Nothing about cost: no price, budget or paise column anywhere.
-- =====================================================================

-- ---------------------------------------------------------------------
-- notification_rule: one row per automatic message. The Settings screen's "Automatic messages" table.
-- Example: FEE_REMINDER_10TH, kind FEE_REMINDER, CRON '0 0 10 10 * *', audience FAMILIES_WITH_DUES, off.
-- The seed rows have no template: the owner picks one in Settings, and only then can turn the rule on
-- (docs/08-decisions.md).
-- ---------------------------------------------------------------------
create table notification_rule (
    id              bigint generated always as identity primary key,
    code            varchar(40)  not null unique,          -- 'FEE_REMINDER_10TH', 'BIRTHDAY', 'BUS_LATE', 'WELCOME'
    name            varchar(80)  not null,                 -- the sender on Home: "Automatic · Fee reminder 10th"
    kind            varchar(20)  not null check (kind in ('NOTICE', 'URGENT', 'MARKETING', 'GREETING', 'FEE_REMINDER', 'ONE_FAMILY', 'STAFF')),
    template_id     bigint       references message_template (id),
    audience        jsonb        not null,                 -- same shape as notification.audience
    trigger_type    varchar(10)  not null check (trigger_type in ('CRON', 'EVENT')),
    cron            varchar(60),                           -- Spring cron, zone Asia/Kolkata: '0 0 10 10 * *'
    event           varchar(30)  check (event in ('ADMISSION_SAVED', 'BUS_LATE', 'NO_MORNING_TAP', 'PAYMENT_SAVED')),
    fixed_values    jsonb        not null default '{}',    -- FESTIVAL: {"dates": ["2027-03-04"], "Festival": {"2027-03-04": "होली"}}
    delay_minutes   int          check (delay_minutes >= 0), -- send later, not at once
    enabled         boolean      not null default false,
    last_run_at     timestamptz,
    updated_by      bigint       references app_user (id),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    -- A CRON rule has a cron and no event; an EVENT rule has an event and no cron.
    constraint notification_rule_trigger check ((trigger_type = 'CRON') = (cron is not null)
                                                and (trigger_type = 'EVENT') = (event is not null)),
    -- A rule with no template cannot be on: it would have nothing to send.
    constraint notification_rule_enabled_template check (not enabled or template_id is not null)
);
create index notification_rule_template_idx on notification_rule (template_id);

-- ---------------------------------------------------------------------
-- notification_rule_run: one row per run. The primary key is what stops a run from happening twice,
-- even when the app restarts and the scheduler fires again.
-- Example: rule 1, run_key '2026-10-10', notification 57.
--   CRON '2026-10-10' · EVENT 'student:129' · BIRTHDAY '2026:student:129' · Run now 'manual:<timestamp>'
-- ---------------------------------------------------------------------
create table notification_rule_run (
    rule_id          bigint      not null references notification_rule (id),
    run_key          varchar(60) not null,
    notification_id  bigint      references notification (id),   -- null when the run found nobody to send to
    ran_at           timestamptz not null default now(),
    primary key (rule_id, run_key)
);
create index notification_rule_run_notification_idx on notification_rule_run (notification_id);

-- V14 left notification.rule_id without a foreign key, because this table did not exist yet.
alter table notification add constraint notification_rule_fk foreign key (rule_id) references notification_rule (id);
create index notification_rule_idx on notification (rule_id) where rule_id is not null;

-- ---------------------------------------------------------------------
-- The eight rules of the Settings screen. All off, no template.
-- Audience filters that are filled at fire time (WELCOME's family, BUS_LATE's route) are empty here.
-- DOCUMENTS_MISSING_MONDAY has no audience yet: the code has no "missing documents" list (docs/08-decisions.md).
-- ---------------------------------------------------------------------
insert into notification_rule (code, name, kind, audience, trigger_type, cron, event, fixed_values) values
    ('FEE_REMINDER_10TH',       'Fee reminder 10th',        'FEE_REMINDER',
     '{"groups":["FAMILIES_WITH_DUES"],"filter":{"skipPromised":true}}',       'CRON',  '0 0 10 10 * *',      null,             '{}'),
    ('FEE_REMINDER_20TH',       'Fee reminder 20th',        'FEE_REMINDER',
     '{"groups":["FAMILIES_WITH_DUES"],"filter":{"skipPromised":true}}',       'CRON',  '0 0 10 20 * *',      null,             '{}'),
    ('BIRTHDAY',                'Birthday wish',            'GREETING',
     '{"groups":["BIRTHDAY_TODAY"],"filter":{}}',                              'CRON',  '0 0 6 * * *',        null,             '{}'),
    ('FESTIVAL',                'Festival greeting',        'GREETING',
     '{"groups":["CURRENT_PARENTS","STAFF_ALL"],"filter":{}}',                 'CRON',  '0 0 9 * * *',        null,             '{"dates":[],"Festival":{}}'),
    ('WELCOME',                 'Welcome after admission',  'ONE_FAMILY',
     '{"groups":["ONE_FAMILY"],"filter":{}}',                                  'EVENT', null,                 'ADMISSION_SAVED', '{}'),
    ('BUS_LATE',                'Bus running late',         'URGENT',
     '{"groups":["ROUTE"],"filter":{}}',                                       'EVENT', null,                 'BUS_LATE',       '{}'),
    ('NO_MORNING_TAP',          'Child did not board',      'URGENT',
     '{"groups":["BUS_CHILDREN_WITHOUT_TAP_TODAY"],"filter":{}}',              'CRON',  '0 30 8 * * MON-SAT', null,             '{}'),
    ('DOCUMENTS_MISSING_MONDAY', 'Documents missing, Monday', 'NOTICE',
     '{"groups":[],"filter":{}}',                                              'CRON',  '0 0 10 * * MON',     null,             '{}');
