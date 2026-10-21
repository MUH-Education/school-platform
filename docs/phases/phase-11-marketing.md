Superseded on 21 Oct 2026 by phase-11-notifications.md. Kept for history.

# Phase 11 — Marketing: templates, broadcasts, staff notices

## Goal

The office can write a WhatsApp/SMS template once, get it approved, then send it to many families (or all staff)
at once, and see who got it, who read it, who tapped a button, and who said stop.

This phase needs only Phase 5 (it reuses `message_outbox`, `message_template` and `OutboxWorker` — see
`docs/09-marketing.md`). It does not need Phase 9 (go live) to be finished, but a real send needs a real WhatsApp
provider, same open question (C1) as Phase 5.

## Screens this makes work

- **Message templates**, and **New template**
- **Send a broadcast** (3 steps: choose a template, who gets it, check and send)
- **Broadcasts** (list, report, replies, stop list)

## Read first

- `docs/09-marketing.md` — the whole idea, the flow diagram, and the WhatsApp groups research
- `docs/07-messaging.md` — the queue table and worker this phase reuses
- `docs/03-data-model.md` → `guardian`, `student_guardian`, `message_outbox`, `message_template`
- `docs/08-decisions.md` → B26 (why not Redis), C12 (WhatsApp provider/group question)

## Tables

`V13__marketing.sql`:

- Stretch `message_template`: add `kind` (`SYSTEM`/`NOTICE`/`MARKETING`, existing rows become `SYSTEM`), `media_key`, `example_values`.
- New `message_template_button` (`template_id`, `seq_no`, `type`, `text`, `action`).
- New `broadcast` (`name`, `template_id`, `status`, `scheduled_at`, `created_by`, `recipient_groups`, `class_filter`, `route_filter`, `village_filter`, `family_count`, `cost_paise`).
- Stretch `message_outbox`: add nullable `broadcast_id` (→ `broadcast`).
- New `message_stop` (`guardian_id`, `reason`, `broadcast_id`, `created_at`).

`whatsapp_group` (small internal groups, e.g. a route's driver and attendants) is **out of scope** for this phase — see "Out of scope" below and `docs/09-marketing.md`'s WhatsApp groups section.

## Endpoints

See `docs/06-api.md` → Marketing.

## Rules

1. A template is `NOTICE` or `MARKETING`, chosen when it is made. It cannot change kind later — make a new template instead (matches the screen: changing kind would change the price and the stop-list rule for every past send).
2. A new template starts `approval = WAITING`. Nothing can be put in a broadcast until `approval = APPROVED`. A template that comes back `REJECTED` shows the provider's reason (from the screen: "The text ends with a {Child name} box...").
3. Every `MARKETING` template automatically gets one more line of text and one `REPLY` button whose `action` is `STOP_MESSAGES`. The office cannot remove it. `NOTICE` templates do not get it.
4. **Who gets a broadcast** (`recipient_groups`, one or more of `CURRENT_PARENTS`, `ENQUIRY_OPEN`, `ENQUIRY_LOST`, `STAFF_ALL`):
   - `CURRENT_PARENTS` = the primary guardian of every ACTIVE student. One message per family, not per child.
   - `ENQUIRY_OPEN` / `ENQUIRY_LOST` only make sense for `MARKETING` templates (400 if picked for a `NOTICE`).
   - `STAFF_ALL` reads active `staff` rows, never `guardian`, and is never affected by the stop list.
   - A guardian in two ticked groups is counted once (`select distinct`).
   - Optional `class_filter`, `route_filter`, `village_filter` narrow `CURRENT_PARENTS` further (an enquiry has no class/route/village to filter by, so these only touch `CURRENT_PARENTS`).
5. **The stop list** blocks `MARKETING` only. `NOTICE`, bus SMS, fee receipts and fee reminders always go, to anyone. A family is added by: tapping the auto-added "Stop messages" button (webhook from the provider), writing "STOP" (matched case-insensitively in an incoming message), or by hand from the Broadcasts screen (`reason = TAPPED_BUTTON` / `WROTE_STOP` / office removed by hand uses a normal `DELETE`, not a new reason).
6. **Sending:**
   - `POST /broadcasts` only resolves and counts recipients — it answers the numbers the "Who gets it" screen shows (`familyCount`, `onWhatsApp`, `onSms`, counted after de-duplication and the stop list). It does not write any `message_outbox` rows yet. Status `DRAFT`.
   - `POST /broadcasts/{id}/send` with `sendNow=true` resolves the final recipient list again (something may have changed since step 2) and writes one `QUEUED` `message_outbox` row per recipient, in one transaction, status `SENDING` → `SENT` once every row has left `QUEUED` (checked by `BroadcastScheduler`, see rule 7).
   - `sendNow=false` needs `scheduledAt` (must be in the future) and leaves the broadcast `SCHEDULED`. No `message_outbox` rows exist yet, so `DELETE /broadcasts/{id}` on a `SCHEDULED` broadcast is a plain cancel (matches the screen: "You can cancel it until then").
   - `POST /broadcasts/{id}/test` writes one `message_outbox` row, `purpose = TEST`, to the caller's own phone (from their user/staff row), not counted in `family_count`.
7. **`BroadcastScheduler`** (new, same shape as `OutboxScheduler`) runs every minute. A `SCHEDULED` broadcast whose `scheduled_at` has passed gets its rows written and becomes `SENDING`, same as rule 6's "send now" path. Off in `test` (`app.messaging.broadcast-scheduler-enabled=false`), tests call `runOnce()`.
8. A broadcast's report (`GET /broadcasts/{id}/report`) reads only `message_outbox` rows where `broadcast_id` matches, grouped by `status`. No separate report table.
9. Cost: `costPaise` on a broadcast = `familyCount × price-per-channel`, read from `app_setting` (`marketing.whatsapp_price_paise`, `marketing.sms_price_paise`, separate for `MARKETING` and `NOTICE` kind). Shown before sending (step 3) and summed per month for the budget bar.
10. Templates, broadcasts and the stop list all write `audit_log` rows (`entity_type` = `MESSAGE_TEMPLATE`, `BROADCAST`, `MESSAGE_STOP`), masked phone in `details`, same pattern as every other feature.

## Tasks

- [ ] 11.1 `V13__marketing.sql`.
- [ ] 11.2 `Permission.MARKETING_VIEW`, `MARKETING_EDIT`, `MARKETING_SEND`, all OWNER only (`docs/05-roles-permissions.md`).
- [ ] 11.3 `marketing` service package: `MessageTemplateService` (extend to kind/media/buttons, rule 1–3), `BroadcastService` (rules 4–7), `MessageStopService` (rule 5).
- [ ] 11.4 Recipient resolver: one class per group (`CurrentParentsResolver`, `EnquiryResolver`, `StaffResolver`), each returning a de-duplicated `(guardianId/staffId, phone)` list — same shape so `BroadcastService` does not care which group it is reading.
- [ ] 11.5 `BroadcastScheduler` (rule 7), off by default in `test`.
- [ ] 11.6 Cost calculation (rule 9), settings added to `app_setting`.
- [ ] 11.7 Report endpoint (rule 8) and `GET /message-stops`.
- [ ] 11.8 Stop-list webhook handling: incoming "STOP" text or a tapped `STOP_MESSAGES` button closes the loop (needs the provider's inbound webhook — same open question C1 as Phase 5's real provider).
- [ ] 11.9 Audit rows (rule 10).
- [ ] 11.10 Dev data: a few sample templates (one of each kind, one WAITING, one REJECTED) and one sent broadcast, for the screen to show something.

## Tests that must pass

- `templateCannotBeUsedUntilApproved`
- `marketingTemplateAlwaysGetsTheStopButton`
- `noticeTemplateNeverBlockedByStopList`
- `familyWithThreeChildrenGetsOneMessage`
- `familyInTwoGroupsCountedOnce`
- `familyOnStopListSkippedForMarketing`
- `familyOnStopListStillGetsANotice`
- `staffBroadcastNeverChecksTheStopList`
- `testSendGoesOnlyToCallersOwnPhoneAndIsNotCounted`
- `scheduledBroadcastCanBeCancelledBeforeItFires`
- `scheduledBroadcastFiresOnceAtTheRightTime`
- `reportCountsMatchTheOutboxRows`
- Permission tests: every endpoint needs `MARKETING_VIEW`/`MARKETING_EDIT`/`MARKETING_SEND`; every role except OWNER gets 403; no token gets 401.

## Done when

- A template can be made, sent for approval, approved (by hand in dev/test, since there is no real WhatsApp check yet), and used in a broadcast.
- A broadcast to "current parents" sent in `dev` shows up in the console (through `LogSmsSender`/a log WhatsApp sender) once per family, not once per child.
- The Broadcasts report screen's numbers (delivered, read if available, tapped) match a direct count of that broadcast's `message_outbox` rows.
- A family that taps "Stop messages" gets no more Marketing broadcasts, but still gets a Notice and a bus SMS.
- `/check-phase 11` passes.

## Out of scope

- Sending into an existing WhatsApp **group** chat (`whatsapp_group`, see `docs/09-marketing.md`). It needs an Official Business Account with the provider — open question C12 — and is for a small internal circle (staff, a route's crew), never for parent broadcasts.
- A real WhatsApp "read" webhook and "tapped a button" webhook — the report screen's columns exist, but without a real provider (question C1) they stay at 0/empty, same limit Phase 5 already has for real sends.
- Email as a channel.
- A general "digital marketing campaign" tool (UTM links, ad spend tracking, a CRM-style pipeline beyond the existing Enquiries stages). The owner asked for this direction but said not to treat it as final — revisit once broadcasts are in daily use.
