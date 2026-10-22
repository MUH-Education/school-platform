# Phase 12 — Automatic messages (rules)

## Goal

Messages that go on their own, with nobody clicking: fee reminder on the 10th and 20th, birthday wish at 6 am,
festival greeting, welcome after admission, "bus running late" from the attendant's tap, "child did not board" at
8:30. The Settings screen's "Automatic messages" table.

Needs Phase 11. A rule does only one thing: **at the right moment it creates a normal `notification` row** with
`source = RULE`. Everything after that (audience, guard chain, expand, send, report) is Phase 11 and is not
touched.

Real-life picture: Phase 11 is the clerk who sends a letter when the owner asks. Phase 12 is a calendar on the
clerk's wall: "every 10th, send the fee reminder". The clerk still does all the checking.

## Read first

- `docs/10-notifications.md` sections 1, 3, 10 (the guard chain is what makes repeating safe)
- `docs/07-messaging.md` (the bus SMS flow is already an event rule, hard-coded; this phase does not change it)

## Tables

`V15__notification_rules.sql`:

```sql
create table notification_rule (
    id              bigint generated always as identity primary key,
    code            varchar(40)  not null unique,          -- 'FEE_REMINDER_10TH', 'BIRTHDAY', 'BUS_LATE', 'WELCOME'
    name            varchar(80)  not null,
    kind            varchar(20)  not null,                 -- same check as notification.kind
    template_id     bigint       references message_template (id),   -- empty in the seed rows; needed to turn on
    audience        jsonb        not null,                 -- same shape as notification.audience
    trigger_type    varchar(10)  not null check (trigger_type in ('CRON', 'EVENT')),
    cron            varchar(60),                           -- Spring cron, zone Asia/Kolkata: '0 0 10 10,20 * *'
    event           varchar(30),                           -- 'ADMISSION_SAVED', 'BUS_LATE', 'NO_MORNING_TAP', 'PAYMENT_SAVED'
    fixed_values    jsonb        not null default '{}',    -- festival dates and names (rule 7)
    delay_minutes   int          check (delay_minutes >= 0), -- WELCOME: send later, not at once (rule 4)
    enabled         boolean      not null default false,
    last_run_at     timestamptz,
    updated_by      bigint       references app_user (id),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint notification_rule_trigger check ((trigger_type = 'CRON') = (cron is not null) and (trigger_type = 'EVENT') = (event is not null)),
    constraint notification_rule_enabled_template check (not enabled or template_id is not null)
);
create table notification_rule_run (
    rule_id     bigint      not null references notification_rule (id),
    run_key     varchar(60) not null,                      -- CRON (BIRTHDAY too): '2026-10-10'; EVENT: 'student:129'
    notification_id bigint  references notification (id),
    ran_at      timestamptz not null default now(),
    primary key (rule_id, run_key)                          -- the same run never happens twice, even after a restart
);
```

`notification` already has `source` and `rule_id`.

## Rules (with examples)

1. **A rule creates a notification; it never sends by itself.** `RuleService.fire(rule, runKey, fixedValues)` →
   `NotificationService.createFromRule(...)` → status `SENDING` → the Phase 11 dispatcher does the rest. So a
   rule's message is checked by the same guard chain: the fee reminder on the 10th skips families that paid,
   exactly like a manual one.
2. **One run per key.** `notification_rule_run (rule_id, run_key)` is the primary key. `FEE_REMINDER_10TH` on
   10 Oct has key `2026-10-10`. If the app restarts at 10:00:30 and the scheduler fires again, the insert fails
   and nothing is created twice. Birthday is one notification a day for all children born that day (key
   `2026-10-23`), so a child gets one wish a year (owner's choice, 23 Oct 2026).
3. **CRON rules** are checked by `RuleScheduler` every minute: for each enabled CRON rule, compute the last fire
   time of its cron (Spring `CronExpression`) in `Asia/Kolkata`; if it is within the last 2 minutes and the run
   key is not used, fire. A server that was down at 10:00 and comes back at 10:01 still fires; at 10:05 it does
   not (the owner can press "Run now" on the Settings screen instead).
4. **EVENT rules** are fired from the existing services with one line each, after the transaction that saved the
   thing:
   - `AdmissionService.save` → `ruleEvents.publish(ADMISSION_SAVED, studentId)` → WELCOME next morning 9 am
     (the rule has `delay`: the notification is created with `scheduled_at = tomorrow 9:00`).
   - `TripService` "Running late" tap (new small endpoint in the attendant app, Phase 4 style) → `BUS_LATE`
     with `fixedValues {Route, Minutes}` → URGENT to that route's families.
   - `PaymentService.save` → `PAYMENT_SAVED` (used only to cancel: a QUEUED fee-reminder row for that family
     becomes SKIPPED `ALREADY_PAID` at once, without waiting for the worker).
   Events go through Spring's `ApplicationEventPublisher` with `@TransactionalEventListener(AFTER_COMMIT)`, so a
   rolled-back admission never sends a welcome.
5. **`NO_MORNING_TAP`** is a CRON rule at 8:30 on school days that uses audience `BUS_CHILDREN_WITHOUT_TAP_TODAY`
   (one new `RecipientResolver`). Off by default; the owner turns it on when the attendants are reliable.
6. **Birthday** is a CRON rule at 6:00 with audience `BIRTHDAY_TODAY` (new resolver: ACTIVE students with
   `dob` month/day = today). Template kind GREETING, so the stop list applies.
7. **Festival greeting** is a CRON rule with a fixed date list in `fixed_values` (`{"dates": ["2026-10-20",
   "2027-03-04"], "Festival": {"2026-10-20": "दीपावली"}}`); the scheduler fires only on those dates.
8. **Enabling and editing** is OWNER only (`SETTINGS_EDIT`). The Settings screen's switches call
   `PUT /notification-rules/{id}` with `enabled`. Audit row per change.
9. **A rule's notification shows on Home and in Reports** like any other, with "Automatic · Fee reminder 10th" as
   the sender.
10. Nothing about money; no approval; no group posting.

## Endpoints

| Method and URL | Permission | What |
|---|---|---|
| `GET /notification-rules` | MARKETING_VIEW | the Settings table, with last run and last month's counts |
| `PUT /notification-rules/{id}` | SETTINGS_EDIT | enabled, template, audience, cron, time |
| `POST /notification-rules/{id}/run-now` | SETTINGS_EDIT | fire once now (run key `manual:<timestamp>`) |
| `POST /trips/{routeId}/running-late` | TRIPS_RECORD | attendant app: minutes → fires BUS_LATE |

## Tasks

- [x] 12.1 `V15__notification_rules.sql` + seed rows (all `enabled = false`): FEE_REMINDER_10TH, FEE_REMINDER_20TH,
      BIRTHDAY, FESTIVAL, WELCOME, BUS_LATE, NO_MORNING_TAP, DOCUMENTS_MISSING_MONDAY.
- [x] 12.2 Entities, repositories, `RuleRunRepository.tryInsert` (insert … on conflict do nothing → boolean).
- [x] 12.3 `RuleService.fire(...)`, `NotificationService.createFromRule(...)`.
- [x] 12.4 `RuleScheduler` (every minute, `app.notifications.rules-enabled`, off in test, `runOnce(now)`).
- [x] 12.5 `RuleEvents` (`ApplicationEventPublisher`) + listeners; the three publish lines in Admission, Trip,
      Payment services; `running-late` endpoint.
- [x] 12.6 Resolvers `BirthdayTodayResolver`, `BusChildrenWithoutTapTodayResolver`.
- [x] 12.7 Endpoints + audit + docs + dev data + Postman.

## Tests that must pass

- `cronRuleFiresOnceEvenIfSchedulerRunsTwice`
- `cronRuleFiresAfterShortDowntimeButNotAfterLong`
- `feeReminderRuleCreatesNotificationThatSkipsPaidFamilies` (end to end with the Phase 11 guard chain)
- `birthdayRuleWishesEachChildOncePerYear`
- `welcomeRuleIsNotFiredWhenAdmissionRollsBack`
- `busLateEventGoesOnlyToThatRouteToday`
- `paymentSavedSkipsQueuedFeeReminderAtOnce`
- `disabledRuleNeverFires`
- Security: endpoints 401/403.

## Done when

- With the clock set to 10 Oct 10:00 in a test, the fee reminder rule creates one notification; the guard chain
  skips the paid families; the report shows the skip reasons.
- Turning a rule off in Settings stops it; "Run now" fires it once.
- `./gradlew build` green.

## Out of scope

- A rule editor with free cron text in the UI (the UI offers fixed choices: every day at, every month on,
  every week on; the cron is built by the server).
- Rules that send to WhatsApp groups.
