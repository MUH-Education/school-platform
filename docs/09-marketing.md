# 9. Marketing: templates, broadcasts, WhatsApp groups

This is the big "send one message to many people" screen: Message templates, Broadcasts, and later staff notices
and fee receipts going through the same pipe. It is new scope, written up after the owner shared the designs for
Message templates, New template, Send a broadcast (3 steps) and the Broadcasts report screen.

**This builds on Phase 5, it does not replace it.** Phase 5 already has `message_outbox` (the queue table) and
`message_template` (the approved texts) for bus SMS and login OTP. A "broadcast" is just many rows written into
`message_outbox` at once, sent by the same worker (`OutboxWorker`) that already runs every 5 seconds. No Redis,
no second queue system — see decision B26 in `docs/08-decisions.md`.

## The idea in one picture

```
1. Office makes a TEMPLATE                 (Message templates screen)
   "Open day, Sunday 25 October", Marketing, Hindi, poster, 3 buttons
         │
         ▼
2. WhatsApp checks the template              (status WAITING → APPROVED or REJECTED)
   Nothing can be sent on a WAITING or REJECTED template.
         │
         ▼
3. Office makes a BROADCAST from that template     (Send a broadcast, 3 steps)
   Step 1: which template
   Step 2: who gets it (parents of current students, enquiry parents, staff, ...)
   Step 3: send now or later, see the cost, send a test to own phone first
         │
         ▼
4. The server works out the real people
   231 families + 23 enquiry families − 5 said stop − 2 counted twice = 244 families
         │
         ▼
5. One row per family (or per staff member) is written into message_outbox,
   status QUEUED, purpose MARKETING or NOTICE, broadcast_id filled in
         │
         ▼
6. OutboxWorker (already built, Phase 5) picks QUEUED rows every 5 seconds,
   same as it does for bus SMS today
         │
         ▼
7. Row becomes SENT / DELIVERED / FAILED. The Broadcasts report screen is just
   "count the outbox rows of this broadcast_id, grouped by status".
```

Real-life picture: Phase 5 built the letterbox and the postman (the queue table and the worker). A broadcast is
just a morning where the office drops 244 letters in the box instead of 1. The postman does not care — he still
empties the box every 5 seconds.

## Two kinds of message (matches the screen's two tabs)

| | Notice | Marketing |
|---|---|---|
| About | Holidays, timing change, parent-teacher meeting | Admissions, events, festival greetings, offers |
| Goes to | Current parents only | Parents who have not said "stop" |
| Has a "Stop messages" button | No | Yes, on every message |
| Blocked by the stop list | Never | Yes |
| Cost (from the screen) | About ₹0.12 + GST | About ₹0.86 + GST |

A template is one or the other, chosen when it is made (screen "New template", section 1). WhatsApp itself also
makes this call: if a "Notice" template contains an offer, WhatsApp sends it back as rejected and the office must
mark it Marketing instead (seen on the Message templates screen, "Sent back").

## Templates — stretching `message_template`

The existing `message_template` row (code, gender, name, channel, language, body, approvedBody, providerTemplateId,
approval, active) is for one fixed text (`BOARDED_MORNING_M`). A marketing template needs more: a poster image, and
up to 3 buttons. Add:

| New column on `message_template` | Example |
|---|---|
| `kind` | `MARKETING` or `NOTICE` (bus/OTP templates keep a third value, `SYSTEM`, so old rows do not need a guess) |
| `media_key` | `templates/open-day-poster.jpg`, same idea as `PhotoStorage` for student photos |
| `example_values` | `{"Parent name": "Rajesh Punia"}` — what WhatsApp needs to approve a template with boxes like `{Parent name}` |

New table `message_template_button` (one row per button, up to 3):

| Column | Example |
|---|---|
| `template_id` | → `message_template` |
| `seq_no` | 1, 2, 3 (order under the message) |
| `type` | `CALL`, `REPLY` |
| `text` | "स्कूल को कॉल करें" |
| `action` | the phone number (CALL) or what happens on tap (REPLY: `MAKE_ENQUIRY`, `STOP_MESSAGES`, or free text, see rule below) |

Rule seen on the screen: every Marketing template gets one more line added automatically, "ये संदेश नहीं चाहिए तो
STOP लिखें" (reply STOP to stop), and a `REPLY` button whose action is always `STOP_MESSAGES`. The office cannot
remove or change this one.

## Broadcasts — a new table on top of `message_outbox`

| Table | What it holds |
|---|---|
| `broadcast` | One row per "Send a broadcast" run: `name`, `template_id`, `status` (`DRAFT`, `SCHEDULED`, `SENDING`, `SENT`, `CANCELLED`), `scheduled_at`, `created_by`, the filters picked (`recipient_groups`: a small list like `["CURRENT_PARENTS","ENQUIRY_OPEN"]`, plus optional `class_filter`, `route_filter`, `village_filter`), and once sent: `family_count`, `cost_paise`. |
| `message_outbox` (stretch) | Add nullable `broadcast_id` (→ `broadcast`). A broadcast's report is "every outbox row with this `broadcast_id`, grouped by `status`". No new report table needed — same idea Phase 5 already uses for the Messages screen. |

### Who can be in a broadcast (recipient groups)

Matches the "Who gets it" screen exactly:

| Recipient group | Where it comes from | Used for |
|---|---|---|
| `CURRENT_PARENTS` | Every `guardian` with an active child (via `student_guardian`), one message per family (the primary guardian), not per child | Notices and Marketing |
| `ENQUIRY_OPEN` | Guardians on an enquiry that is NEW/CONTACTED/VISITED/APPLIED | Marketing only |
| `ENQUIRY_LOST` | Guardians on a LOST enquiry | Marketing only |
| `STAFF_ALL` | Every active `staff` row's own phone | A different screen flow — see "Staff broadcasts" below |

A family with 3 children in the school counts once (the existing "primary guardian = family" idea from
`docs/03-data-model.md` B-rule). A family in two ticked groups also counts once — `select distinct` on the
guardian id before rows are written, same safety the screen's summary box shows ("−2 are in two groups").

### The stop list

New table `message_stop` (or a column — a table is better, it keeps *when* and *which broadcast caused it*,
both shown on the report screen):

| Column | Example |
|---|---|
| `guardian_id` | → `guardian` |
| `reason` | `TAPPED_BUTTON`, `WROTE_STOP` |
| `broadcast_id` | which message had the button (nullable, `WROTE_STOP` has none) |
| `created_at` | |

A guardian on this list is skipped for `MARKETING` broadcasts only. Bus SMS, fee receipts, fee reminders and
`NOTICE` broadcasts are never blocked (this matches the screen's own text exactly: "Bus SMS, fee receipts and
notices still go"). The office can remove someone from the list by hand (the "Take off the list" button); that
is a normal delete with an audit row, same pattern as other "undo" actions in this app.

### Sending: now, later, and the daily limit

- `Send now`: the broadcast's rows go into `message_outbox` as `QUEUED` right away; `OutboxWorker` drains them over
  the next few minutes, same as any other queued message.
- `Send later`: the broadcast stays `SCHEDULED`. One more small job, `BroadcastScheduler` (same shape as
  `OutboxScheduler`), checks every minute for a `SCHEDULED` broadcast whose `scheduled_at` has passed, and turns
  its rows into `QUEUED` at that moment — it does not create the rows early, so "cancel until then" (seen on the
  screen) is just deleting the `DRAFT`/`SCHEDULED` broadcast row, nothing to undo in the queue yet.
- `Send a test to my phone`: one `message_outbox` row, `purpose = TEST`, straight to the owner's own number, not
  counted in `family_count` or the report. `MessagePurpose.TEST` already exists for this.
- WhatsApp's own daily cap ("2 marketing messages a day from all businesses") is not something this app enforces
  — the provider enforces it and reports failures back. The app just retries a failed row the normal way
  (`OutboxWorker` already retries 3 times before `FAILED`).

### Cost

The screen shows a cost estimate before sending and a running "spent this month" bar. Both numbers come from the
same place: a per-message price kept in `app_setting` (like other school-wide numbers), multiplied by how many
rows the broadcast is about to create. `About ₹0.86 + GST` for a Marketing WhatsApp message and `₹0.12 + GST` for
a Notice are settings, not hard-coded, since the provider can change prices.

## WhatsApp groups — what is and is not possible

Checked against Meta's own developer docs (`developers.facebook.com/documentation/business-messaging/whatsapp/groups`,
page last updated 21 May 2026):

- **Sending into an existing WhatsApp group is now possible** through the official WhatsApp Business Platform: a
  message with `recipient_type: group` and `to` set to the group's id. Text, media, and template messages are
  supported this way. It is new — it did not exist when most WhatsApp API guides were written, so a lot of
  write-ups still say "not possible."
- **It has real limits that matter for a school:**
  - It needs the account to be an **Official Business Account** (a status your WhatsApp provider/BSP applies for
    with Meta, not a checkbox the app can turn on).
  - Group size is small (around 8 participants in what we could confirm) — nowhere near "all 244 parents in one
    group."
  - There is no API to add a person to a group directly; people join through an invite link.
  - Interactive buttons are not supported inside a group message.
- **What this means for the design:** a WhatsApp group is **not** the way to broadcast to parents. The broadcast
  flow above (one `message_outbox` row per family, sent as a normal 1:1 template message) stays the one and only
  way to reach many parents — which is exactly what the screenshots already show (244 separate sends, not one
  group post). A WhatsApp group is only useful for a **small, fixed** circle talking to each other, for example
  "Route 4 driver + 2 attendants + transport in-charge" or "all teachers." Treat it as its own small feature, not
  part of broadcasts:

  | Table | What it holds |
  |---|---|
  | `whatsapp_group` | `name`, `provider_group_id` (the id your WhatsApp provider gives back), `purpose` (free text, e.g. "Route 4 crew"), `active` |

  Sending to it would be one more `MessageChannel`-shaped sender (`WhatsAppGroupSender`), reusing `message_outbox`
  with a new nullable `whatsapp_group_id` column (instead of `phone`) — same shape, not built in this pass.

- **Before relying on this for anything real:** eligibility (Official Business Account), group size, and pricing
  need to be confirmed with whichever WhatsApp provider the school signs up with (same open question C1 as all of
  Phase 5 — "which company for SMS and WhatsApp"). Written up as open question C12 in `docs/08-decisions.md`.

## Staff broadcasts

Same `broadcast` flow, different source of phones: `recipient_groups` includes `STAFF_ALL`, which reads active
`staff` rows instead of `guardian` rows. A staff broadcast is never blocked by the parent stop list (it is a
different table). Example: "School closed tomorrow for the election" goes to all 40 staff phones through the same
pipe that sends it to 244 parent families — two broadcasts from one template, or two templates, the office's
choice.

## Fee receipts and a future payment gateway

Nothing new needed here — it is already wired for this. `MessagePurpose.FEE_RECEIPT` and `FEE_REMINDER` exist in
the code today (added in Phase 5) but nothing uses them yet. The day a receipt is printed (now, by the office) or
a payment gateway sends a webhook (later), the fee service calls the same method `MessageService.queue(...)` that
`OutboxBoardingNotifier` already calls for a bus tap, with `purpose = FEE_RECEIPT`. One template
(`message_template`, `kind = SYSTEM`), one row per payment, same worker sends it. A future payment gateway is just
one more caller of that one method — no new queue, no new worker.

## Extending this later (the point of writing it this way)

| To add | Do this |
|---|---|
| A new channel, e.g. email | One new class shaped like `SmsSender`/`OtpSender` (`EmailSender`), add `EMAIL` to `MessageChannel`. Nothing else changes — same pattern Phase 5 already uses for SMS providers. |
| A new reason to message someone | One new `MessagePurpose` value, one template row. |
| A new recipient group, e.g. "parents of Class 9–10 only" | One more filter on `broadcast`, same shape as the screen's Class/Route/Village dropdowns. |
| A new kind of campaign report (open rate by village, best time of day, ...) | Read from `message_outbox` rows, grouped by `broadcast_id` — the raw data is already there, this is a new query, not a new table. |

## Entities — summary

```
message_template (stretch: + kind, media_key, example_values)
  └─ message_template_button (new)

broadcast (new)
  └─ message_outbox (stretch: + broadcast_id, + whatsapp_group_id)

message_stop (new)

whatsapp_group (new, small internal groups only — not for parent broadcasts)
```

## Open questions (added to `docs/08-decisions.md`, section C)

- C12: Which WhatsApp provider gives Official Business Account status and group messaging, and at what price? Needed before "WhatsApp groups" (the small internal-circle feature) can be built for real; broadcasts do not need this answer since they never use groups.
