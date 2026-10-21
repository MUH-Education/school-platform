# Prompts for Claude Code — Phase 11, Notification module

Paste these into Claude Code, one at a time, inside the `school-platform` repo. Do the setup prompt once, then
the task prompt for every task (change the task number).

---

## Prompt 0 — setup (once)

```text
Read CLAUDE.md, docs/02-architecture.md, docs/07-messaging.md and docs/10-notifications.md fully before anything.

We are starting Phase 11, the Notification module. The plan is docs/phases/phase-11-notifications.md.
It replaces docs/phases/phase-11-marketing.md: add one line at the top of the old file,
"Superseded on 21 Oct 2026 by phase-11-notifications.md", change nothing else in it.
In docs/phases/README.md set Phase 11's name to "Notification module" and its link to the new file, status "In progress".
In docs/08-decisions.md add one row under section B:
"B27 | Phase 11 is a Notification module, one table `notification` expanded by a poller into `message_outbox`.
No cost, no approval step, no repeat on the send screen, no WhatsApp group posting, no Redis. AI refine is a
suggestion only. Files are stored in our own storage first (FileStorage), the provider gets them at send time. |
Owner's decisions after reviewing the Notification canvas, 21 Oct 2026. | Low."

Decisions that are final, do not re-open them:
- One entity `notification` (kind, template, body, media key, audience as jsonb, status, schedule). One row per send.
- `NotificationDispatcher` polls `notification` by status every 60 seconds and writes the `message_outbox` rows.
  The existing `OutboxWorker` sends them. Two tables, two pollers, no queue library.
- Kinds: NOTICE, URGENT, MARKETING, GREETING, FEE_REMINDER, ONE_FAMILY, STAFF. Each has a `KindPolicy` class.
  Adding a kind = enum value + one policy class + a check constraint. Nothing else may need a change.
- Audiences are `RecipientResolver` classes, one per `AudienceGroup`, all returning `List<Recipient>`.
- Channels are `ChannelSender` classes, one per `MessageChannel`. `OutboxWorker` routes by channel.
- Media: `FileStorage` (rename of `PhotoStorage`), `MediaPublisher` uploads to the provider at send time.
- AI: `TextRefiner` interface, `ClaudeTextRefiner` + `LogTextRefiner`, prompt in a resource file,
  placeholders must survive, the office always clicks "Use this".
- No money anywhere: no cost, price, paise or budget fields, settings or DTOs.
- No approval step, no repeat option on a notification, no WhatsApp group sending, no inbox UI.

Code rules on top of CLAUDE.md:
- Interfaces at the top of `service.notification`, implementations in `service.notification.impl`.
- A policy, resolver or sender class must be addable without editing a switch statement: find them by a Map built
  from the Spring beans (`Map<NotificationKind, KindPolicy>` from `List<KindPolicy>` in a config class).
- Every service method that changes a notification checks the status it expects first and throws
  `NOTIFICATION_NOT_EDITABLE` otherwise.
- Every poller method is `runOnce()` returning how many it handled, scheduler class separate, off in test.
- Never log a phone unmasked, never log a message body, never log the AI key.

Do only Prompt 0 now: the three doc edits. Then stop and show me the diff.
```

---

## Prompt for task 11.6b (guard chain) — paste this instead of Prompt N

```text
Phase 11 task 11.6b: the guard chain. Read docs/10-notifications.md section 10 twice.
Build RecipientGuard (order, appliesTo, check), SkipReason, GuardChain (beans sorted by order), and the 13 guard
classes in service.notification.impl. FeeDueGuard must read v_fee_month at call time (JdbcClient, no cache) and
fill {Amount due}, {Months}, {Late fee} into the recipient values. LastSecondGuard is called by OutboxWorker right
before ChannelSender.send for FEE_REMINDER and URGENT rows. A failed check writes a SKIPPED row with skip_reason,
never throws. Tests: every guard has one test that skips and one that passes; plus
feeReminderSkipsFamilyThatPaidAfterScheduling and lastSecondGuardSkipsPaymentBetweenExpandAndSend using
MutableClock and a real payment saved through PaymentService. Run ./gradlew test, tick 11.6b, 3 lines for me.
```

---

## Prompt N — one task

```text
Phase 11, Notification module. Open docs/phases/phase-11-notifications.md and do task 11.<N> only.
Read docs/10-notifications.md section(s) that task touches before writing code.

Order inside the task: migration → entity → repository → service → controller → tests.
Write the tests from the "Tests that must pass" list that belong to this task; they must be green.
Run ./gradlew test. When green, tick the task box in the phase file.
If you had to decide something the docs do not say, add one line to docs/08-decisions.md section B.
If the code and a doc disagree, stop and ask me; do not pick one silently.

At the end tell me in 3 to 5 simple lines: what you built, how I can try it (a curl or a test name), what is next.
```

---

## Prompt for the first big task (11.1, migration) — paste this instead of Prompt N

```text
Phase 11 task 11.1: write src/main/resources/db/migration/V14__notifications.sql exactly as
docs/10-notifications.md sections 4 and 5 describe (V13 is already used).

Checklist:
- message_template: add kind (default 'SYSTEM' for existing rows, then make it not null), header_type
  (default 'NONE'), media_key, sms_body, example_values jsonb default '{}', provider_template_name,
  provider_language default 'hi', rejection_reason, replaces_id (self reference), created_by.
  Check constraints with every enum value listed in the doc.
- message_template_button: template_id, seq_no 1..3, type CALL|URL|REPLY, text (max 25 letters, WhatsApp's
  limit), action; unique (template_id, seq_no).
- notification: exactly the SQL in the doc, plus the two partial indexes.
- message_outbox: add notification_id (fk), read_at, button_action, media_key, values jsonb default '{}';
  extend the purpose check with NOTICE, MARKETING, URGENT, STAFF, ONE_FAMILY; index (notification_id, status).
- message_stop: guardian_id unique fk, reason TAPPED_BUTTON|WROTE_STOP, notification_id, created_at.
- inbound_message: as in the doc, provider_ref unique, index (phone, received_at desc), index on handled where false.
- app_setting rows: notifications.quiet_from '21:00', notifications.quiet_to '08:00',
  notifications.max_marketing_per_family_per_week '2'.
- Nothing about cost, price, paise, budget.

Then MigrationV14Test in the same style as MigrationV9Test: every table exists, every check refuses a bad value
(for example kind = 'COST' on notification), the unique on message_stop refuses a second row for the same guardian,
existing message_template rows have kind = 'SYSTEM'.

Also update tools/tables.yaml (purpose, rules, example for each new table) so make-docs.sh can regenerate
docs/03-data-model.md, and regenerate it.

Run ./gradlew test. Tick 11.1. Tell me in 3 lines what you built and how to check it.
```

---

## Prompt for task 11.12 (AI refine) — paste this instead of Prompt N

```text
Phase 11 task 11.12: the AI "Refine" helper. Read docs/10-notifications.md section 7.

Build:
- service.notification.TextRefiner (interface): RefineResult refine(RefineRequest r).
  RefineRequest(text, RefineAction action, String instruction, String language, Set<String> placeholders).
  RefineResult(text, note).
- impl.ClaudeTextRefiner: calls the Claude Messages API with Spring's RestClient (no new library),
  model from app.ai.model, key from app.ai.api-key (env APP_AI_API_KEY), timeout app.ai.timeout (20s).
  System prompt from classpath prompts/refine.txt. User message = action name, the instruction if any,
  the language, and the text. Expect the answer to be the new text only.
- impl.LogTextRefiner: active when the key is empty or in test; returns the input unchanged with note
  "AI is not set up".
- After any refiner answers: every placeholder of the input (anything matching \{[^}]+\}) must be in the output,
  else throw BusinessException("REFINE_LOST_PLACEHOLDER"). Also trim and cap at 1024 letters.
- POST /api/v1/notifications/refine, permission MARKETING_SEND, body { text, action, instruction, language },
  answer { text, note }. 409 REFINE_NOT_CONFIGURED when the key is empty in a non-test profile.
- Never log the text or the key. Count letters, not bytes, for the note ("118 letters shorter").

prompts/refine.txt must say, in English: you edit short school messages for Indian parents in Hindi or English;
keep every {box} exactly as written; keep names, amounts, dates, times and phone numbers; keep the school name
"MUH Jain Global School" or "MUH Jain School"; no emoji; answer with the message text only, nothing else;
then one line per action explaining what SHORTER, BETTER_HINDI, WARMER, ENGLISH, FIX, SMS_FIT (70 letters, no boxes)
and CUSTOM mean.

Tests: refineKeepsPlaceholdersOrFails (mock the HTTP call with MockRestServiceServer: one answer that drops
{Child name} → 409; one good answer → 200), refineOffWithoutKey, endpoint 401/403.
Run ./gradlew test, tick 11.12, 3 lines for me.
```

---

## Phase 12 (automatic rules) — same Prompt N shape, file docs/phases/phase-12-automatic-rules.md

```text
Phase 12, Automatic messages. Open docs/phases/phase-12-automatic-rules.md and do task 12.<N> only.
A rule only creates a notification row; it never sends. The same guard chain from Phase 11 does the checking.
Same working rules as Phase 11 prompts. 3 lines at the end.
```

---

## Check prompt (after all tasks)

```text
Phase 11 "Done when" check. Start the app in dev with ./gradlew bootRun and, with curl and the dev OWNER login
(see DevDataLoader), do every line of the "Done when" list in docs/phases/phase-11-notifications.md.
Paste each command and the important part of its answer. Then grep -ri "cost\|price\|paise\|budget"
src/main/java/com/muhjain/school/service/notification and show the result (must be empty).
If everything passes, set Phase 11 to Done in docs/phases/README.md.
```
