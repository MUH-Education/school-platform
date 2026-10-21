# 10. Notification module — the design

**Replaces the "broadcast" part of `docs/09-marketing.md`.** Decisions made on 21 Oct 2026 after the owner
reviewed the Notification canvas (screens 0 to 9b):

| Decided | Why |
|---|---|
| The word is **Notification**, not Broadcast. One table `notification` holds everything the office sends: who, what, when, with which file. | The owner's own word. One entity, many kinds. |
| **No cost** anywhere in the UI or the API. | "Looks cheap." Prices change; the provider's bill is the truth. |
| **No approval step.** A user with the permission sends; a user without it cannot. | Owner is the only sender for marketing. No second person to approve. |
| **No "repeat" on the send screen.** Repeating messages (fee reminder on the 10th, birthday wish) are a separate thing: `notification_rule`, Phase 12. | The list must be made fresh each time, so a family that paid is not reminded again. A rule does that; a repeated notification would not. |
| **No WhatsApp group posting.** The owner posts the poster into the parent groups by hand from the school phone. | Option 1 of the Whapi discussion: ₹0, no ban risk. |
| **No Redis, no queue system.** Two tables, two pollers: `notification` (polled by `NotificationDispatcher`) and `message_outbox` (polled by the existing `OutboxWorker`). | B26. The traffic is tiny: 400 bus SMS a day, a broadcast of 250 a few times a month. |
| **AI "Refine" is a helper, never automatic.** It suggests a new text; the office clicks "Use this". | The school never sends text nobody read. |
| **Files go to the school's own storage first** (local folder in dev, S3 in prod), the provider gets them only at send time. | Same as photos (B17). The file is ours; the provider's media id expires in 30 days. |

Real-life picture: the office writes a letter once (`notification`), the clerk makes 244 copies with each family's
name (`message_outbox` rows), the postman (`OutboxWorker`) delivers them one by one and writes on each copy what
happened.

## 1. The flow

```
Office (React)                              Backend                                         Provider
──────────────                              ───────                                         ────────
POST /notifications  (DRAFT) ─────────────► NotificationService.create
   kind, template, body, mediaKey,            └─ validates with KindPolicy (who may send, which audiences)
   audience                                   └─ RecipientResolver.count → 246 families   (answer only, no rows)
POST /notifications/{id}/test ────────────► one message_outbox row, purpose TEST, to the caller's phone
POST /notifications/{id}/send ────────────► sendNow:  status SENDING  (rows not written yet)
   {sendNow | scheduledAt}                    later:   status SCHEDULED, scheduled_at

              every 60 s   NotificationDispatcher.runOnce()
              ───────────────────────────────────────────────
              select … from notification
               where status = 'SCHEDULED' and scheduled_at <= now()
                  or (status = 'SENDING' and expanded_at is null)
               for update skip locked
              for each:
                 RecipientResolver.resolve(audience)           → list of (person, phone, channel, values)
                 MediaPublisher.publish(mediaKey)               → provider_media_id, cached on the notification
                 one message_outbox row per person, QUEUED, notification_id = id
                 notification.expanded_at = now(), recipient_count = n

              every 5 s    OutboxWorker.runOnce()   (exists since Phase 5, small change: channel routing)
              ──────────────────────────────────────────────────────────────────────────────────────────
              claimQueued(50) → for each row: ChannelSender.forChannel(row.channel).send(row) ──────────► WhatsApp / SMS
                 SENT + provider_ref | FAILED after 3 tries

              webhooks   POST /webhooks/{provider}/status  ◄──────────────────────────────────────────── delivered / read / failed
              ────────   POST /webhooks/{provider}/inbound ◄──────────────────────────────────────────── text "STOP", button tap
                         → message_outbox.status, delivered_at, read_at
                         → message_stop (STOP), inbound_message (every incoming text)

              every 60 s   NotificationDispatcher also closes notifications:
                           SENDING and no QUEUED rows left → SENT, finished_at

GET /notifications/{id}/report ◄─────────── count(message_outbox) where notification_id = ? group by status
```

Three things to notice:

1. **Rows are written by the dispatcher, not by the controller.** "Send now" only flips the status. So a request
   never takes long, and a crash between "send" and "rows written" is safe: the dispatcher sees `SENDING` with
   `expanded_at` null and does it on the next minute. `for update skip locked` means two app instances never
   expand the same notification.
2. **The list is made at send time, not at draft time.** A family that says STOP at 6:29 pm is not in the 6:30 pm
   list.
3. **One kind of row for everything.** Bus SMS (Phase 4), fee receipts (Phase 7) and notifications all end up as
   `message_outbox` rows. The report is a `group by`. The Messages screen is a `select`.

## 2. Kinds of notification

`NotificationKind` is an enum. Every kind has a `KindPolicy` (one small class per kind, found by a map in
`KindPolicies`). **To add a kind: one enum value, one policy class, one Flyway `check` change. Nothing else.**

| Kind | Who may send (later — today every kind is `MARKETING_SEND`, owner only) | Audiences allowed | Stop list applies | Needs an approved template | Quiet hours |
|---|---|---|---|---|---|
| `NOTICE` | OWNER, OFFICE_ADMIN, ADMISSIONS_DESK | CURRENT_PARENTS (+ filters), ONE_FAMILY | No | Yes (a saved notice is a template of kind NOTICE) | Respected |
| `URGENT` | OWNER, OFFICE_ADMIN; TRANSPORT_INCHARGE for ROUTE only | CURRENT_PARENTS, ROUTE, STAFF | No | Yes (fixed urgent texts) | **Ignored**, sends at once, WhatsApp **and** SMS |
| `MARKETING` | OWNER | CURRENT_PARENTS, ENQUIRY_OPEN, ENQUIRY_LOST, STAFF | **Yes** | Yes | Respected |
| `GREETING` | OWNER, OFFICE_ADMIN | same as MARKETING | **Yes** (WhatsApp counts it as marketing) | Yes | Respected |
| `FEE_REMINDER` | OWNER, OFFICE_ADMIN | FAMILIES_WITH_DUES (+ filters: months late, amount) | No | Yes (3 saved tones) | Respected |
| `ONE_FAMILY` | OWNER, OFFICE_ADMIN, ADMISSIONS_DESK | ONE_FAMILY | No | Yes, or free text inside the 24-hour window | Respected |
| `STAFF` | OWNER, OFFICE_ADMIN; TRANSPORT_INCHARGE for transport staff | STAFF_ALL, DRIVERS, ATTENDANTS, TEACHERS, ROUTE_CREW, ONE_STAFF | No | Yes, or free text inside the 24-hour window | Respected |

```java
public interface KindPolicy {
    NotificationKind kind();
    Set<AudienceGroup> allowedAudiences();
    boolean stopListApplies();
    boolean ignoresQuietHours();
    boolean alsoSmsWhenOnWhatsApp();          // URGENT = true
    Set<Permission> whoMaySend();
    MessagePurpose outboxPurpose();           // NOTICE → NOTICE, MARKETING/GREETING → MARKETING, ...
}
```

## 3. Audiences

`AudienceGroup` enum + one `RecipientResolver` per group. All return the same shape, so `NotificationService`
never cares which group it is reading:

```java
public record Recipient(RecipientType type,   // GUARDIAN | STAFF | ENQUIRY
                        Long personId,
                        String phone,            // +91…, null = no phone (counted, never sent)
                        MessageChannel channel,  // WHATSAPP for now (no "has WhatsApp" flag exists); SMS after NOT_ON_WHATSAPP
                        AudienceGroup group,     // the group that found this person first
                        List<Long> studentIds,   // the ACTIVE children of a family; empty for staff and enquiries
                        Map<String, String> values) {}  // {Parent name}, {Child name}, {Class}, {Amount due} …

public interface RecipientResolver {
    Set<AudienceGroup> groups();                 // one class may serve several groups (StaffResolver: 4)
    List<Recipient> resolve(AudienceGroup group, AudienceFilter filter);
}
```

Box names every recipient has: `Name`. Families also `Parent name`, `Child name` (first names, "Aryan और Siya"),
`Class`; ROUTE adds `Route`; FAMILIES_WITH_DUES adds `Amount due` ("₹8,500"), `Months` ("सितंबर और अक्टूबर"),
`Months late`. Staff: `Staff name` (ROUTE_CREW adds `Route`). Enquiries: `Parent name`, `Child name`, `Class`.

| Group | Reads | Rule |
|---|---|---|
| `CURRENT_PARENTS` | `student` (ACTIVE) → `student_guardian` (primary) → `guardian` | **One row per family**, children joined in `{Child name}`: "Aryan और Siya". Filters: classes, routeIds, villages, feeStatus. |
| `ROUTE` | `transport_enrolment` (uses_bus, today) → families on that route | For URGENT "bus late". |
| `ENQUIRY_OPEN` / `ENQUIRY_LOST` | `enquiry` by status | Never for NOTICE / FEE_REMINDER. |
| `FAMILIES_WITH_DUES` | `v_fee_month` + `fee_due` | Values: `{Months}`, `{Amount due}`, `{Late fee}`. Filters: minMonthsLate, minAmount, skipRemindedWithinDays, skipPromised (from `inbound_message` flagged PROMISED). |
| `STAFF_ALL`, `DRIVERS`, `ATTENDANTS`, `TEACHERS` | `staff` (active) by `staff_type` (no OFFICE type exists, so no OFFICE group for now) | Never touched by the stop list. |
| `ROUTE_CREW` | `vehicle_assignment` for today | Driver + attendants of one route. |
| `ONE_FAMILY` / `ONE_STAFF` | one id | Which parent: primary, mother, both. |

`NotificationService.resolve()` = union of the ticked groups → **distinct by phone** → minus `message_stop` when
`policy.stopListApplies()` → minus rows with no phone. The counts shown on the "Who gets it" screen are exactly
these subtractions: `picked, duplicates, stopped, noPhone, total, onWhatsApp, onSms`.

The audience is stored on the notification as one `jsonb` column `audience`:

```json
{ "groups": ["CURRENT_PARENTS", "STAFF_ALL"],
  "filter": { "classes": ["10", "12"], "routeIds": [4], "villages": [], "feeStatus": null,
              "minMonthsLate": null, "familyIds": [], "staffIds": [] } }
```

One column, because the shape will change (new groups, new filters) and the filters are only ever read by the
resolvers. A Java record `Audience` maps it (`@JdbcTypeCode(SqlTypes.JSON)`).

## 4. Templates (stretching `message_template`, as Phase 5 left it)

A notification always points to a template, because WhatsApp needs an approved template outside the 24-hour reply
window. The office never sees that rule; it sees "saved texts".

New columns on `message_template`:

| Column | Meaning |
|---|---|
| `kind` | `SYSTEM` (bus, OTP, receipt — the existing rows), `NOTICE`, `URGENT`, `MARKETING`, `GREETING`, `FEE_REMINDER`, `ONE_FAMILY`, `STAFF` |
| `header_type` | `NONE`, `IMAGE`, `VIDEO`, `DOCUMENT` — what the template was approved with |
| `media_key` | the sample file in our storage (the poster the template was approved with) |
| `sms_body` | the short SMS version (70 Hindi letters a part) |
| `example_values` jsonb | `{"Parent name": "Rajesh Punia"}` — WhatsApp wants an example per box |
| `provider_template_name`, `provider_language` | what the provider calls it (`open_day_oct`, `hi`) |
| `rejection_reason` | the provider's text when `approval = REJECTED` |
| `created_by` | who made it |

New table `message_template_button` (`template_id`, `seq_no` 1..3, `type` CALL / URL / REPLY, `text`,
`action` — for REPLY: `STOP_MESSAGES`, `BOOK_VISIT`, `GOT_IT`, `CALL_ME`; for CALL: the phone; for URL: the link).

Rules:
- `kind` is set when the template is made and never changes (the price and the stop-list rule would change for
  every past send).
- A `MARKETING` or `GREETING` template always has a REPLY button `STOP_MESSAGES` as the last button, added by the
  server, cannot be removed.
- Editing an `APPROVED` template makes a **new row** (`replaces_id` → old row, old row `active = false`), approval
  WAITING. Old notifications keep pointing at the old row.
- Approval is set by hand in dev/test (`PUT /notification-templates/{id}/approval`, `MARKETING_EDIT`) and by the provider's
  template-status webhook in prod.

## 5. The `notification` table

```sql
create table notification (
    id                 bigint generated always as identity primary key,
    kind               varchar(20)   not null check (kind in ('NOTICE','URGENT','MARKETING','GREETING','FEE_REMINDER','ONE_FAMILY','STAFF')),
    name               varchar(80)   not null,                      -- office-only name: "Open day, Sunday 25 October"
    template_id        bigint        not null references message_template (id),
    body               varchar(1024) not null,                      -- the text with {boxes}, as the office saw it
    sms_body           varchar(320),                                -- SMS version, null = no SMS fallback
    fixed_values       jsonb         not null default '{}',         -- {Date}: "10 अक्टूबर" — same for everyone
    media_key          varchar(120),                                -- our storage key (poster, video, pdf)
    media_type         varchar(10)   check (media_type in ('IMAGE','VIDEO','DOCUMENT','AUDIO')),
    provider_media_id  varchar(120),                                -- filled by MediaPublisher at send time
    provider_media_at  timestamptz,                                 -- when it was uploaded; re-upload after 25 days
    audience           jsonb         not null,                      -- groups + filter (section 3)
    status             varchar(12)   not null default 'DRAFT' check (status in ('DRAFT','SCHEDULED','SENDING','SENT','CANCELLED','FAILED')),
    scheduled_at       timestamptz,
    expanded_at        timestamptz,                                 -- when the outbox rows were written
    finished_at        timestamptz,                                 -- when the last row left QUEUED
    recipient_count    int,                                         -- counted at expand time
    source             varchar(10)   not null default 'MANUAL' check (source in ('MANUAL','RULE')),
    rule_id            bigint,                                      -- Phase 12: notification_rule
    created_by         bigint        not null references app_user (id),
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    constraint notification_schedule check (status <> 'SCHEDULED' or scheduled_at is not null)
);
create index notification_due_idx on notification (scheduled_at) where status = 'SCHEDULED';
create index notification_open_idx on notification (status) where status in ('SENDING');
```

`message_outbox` gets: `notification_id bigint references notification (id)`, `read_at timestamptz`,
`button_action varchar(30)` (last tapped), `media_key varchar(120)` (null for text), `values jsonb`,
`skip_reason varchar(30)`, status gains `SKIPPED`, and `purpose` gains `NOTICE`, `MARKETING`, `URGENT`, `STAFF`,
`ONE_FAMILY`. Index `(notification_id, status)`; unique `(notification_id, phone)` where notification_id is not null.

New small tables:

- `message_stop` (`guardian_id` unique, `reason` TAPPED_BUTTON / WROTE_STOP, `notification_id`, `created_at`).
- `inbound_message` (`phone`, `guardian_id`, `staff_id`, `enquiry_id`, `text`, `button_action`,
  `in_reply_to_outbox_id`, `provider_ref` unique, `received_at`, `handled` boolean, `handled_by`, `note`). Every
  incoming WhatsApp message lands here; the inbox UI is the provider's for now, this table exists for STOP, button
  taps, the 24-hour window and "skip families who promised".

## 6. Sending through the right channel

Today `OutboxWorker` calls `SmsSender` for every row. The change:

```java
public interface ChannelSender {
    MessageChannel channel();
    SendResult send(MessageOutbox row, SendContext ctx) throws SmsSendException;   // ctx: template, provider ids, media id
}
```

- `SmsChannelSender` wraps the existing `SmsSender` (no behaviour change for bus SMS).
- `WhatsAppChannelSender` calls `ProviderApi.sendWhatsAppTemplate(phone, providerTemplateId, params, mediaUrlOrId)`.
  Params come from the row's `values` (stored as jsonb `values` on the outbox row, so the row is self-contained and
  can be re-sent).
- `OutboxWorker` picks the sender by `row.getChannel()` from a `Map<MessageChannel, ChannelSender>`. In `test`,
  both are the `Log…` versions.
- A WhatsApp row that fails with `NOT_ON_WHATSAPP` makes an SMS row (`fallback_of_id`), using the notification's
  `sms_body`. This already exists for receipts; reuse it.

`MediaPublisher` (interface, `impl.ProviderMediaPublisher`, `impl.LogMediaPublisher`): takes `media_key`, opens it
from `FileStorage`, uploads once per notification, returns the provider media id. Cached on the notification; the
dispatcher re-uploads when `provider_media_at` is older than 25 days (Meta ids live 30).

`FileStorage` = today's `PhotoStorage`, renamed and used for photos, documents and notification media
(`service.storage.FileStorage`, `impl.LocalFolderFileStorage`, Phase 9 adds `S3FileStorage`). Limits by type:
image 5 MB, video 16 MB (MP4), PDF 5 MB, audio 16 MB. The server does not re-encode video in this phase; it
refuses a file over the limit with a clear message ("16 MB max — make it smaller on your phone").

## 7. AI refine

```java
public interface TextRefiner {
    RefineResult refine(RefineRequest r);   // text, action, instruction, language, placeholders
}
```

- `ClaudeTextRefiner` (prod/dev with a key), `LogTextRefiner` (test: returns the input unchanged with a note).
- Actions: `SHORTER`, `BETTER_HINDI`, `WARMER`, `ENGLISH`, `FIX`, `SMS_FIT`, `CUSTOM` (free instruction).
- The system prompt lives in `src/main/resources/prompts/refine.txt`, not in Java. It says: keep every `{box}`
  exactly, keep names, amounts, dates and phone numbers, keep the school name, answer with the text only.
- The server checks the answer: every placeholder that was in the input is still in the output, else 409
  `REFINE_LOST_PLACEHOLDER` and the suggestion is dropped.
- Endpoint `POST /notifications/refine` answers `{ "text": "...", "note": "118 letters shorter" }`. The office
  decides. Nothing is saved.
- Key from `APP_AI_API_KEY`, model name from `app.ai.model`, timeout 20 s, off when the key is empty (409
  `REFINE_NOT_CONFIGURED`). Never log the text.

## 8. API

All under `/api/v1`. **No new permissions and no role changes for now** (owner's decision, 21 Oct 2026, see
`docs/08-decisions.md` section D). The short words in the table map to the permissions that already exist, which
today only OWNER has: VIEW = `MARKETING_VIEW`, TEMPLATES = `MARKETING_EDIT`, SEND = `MARKETING_SEND`.
`KindPolicy.whoMaySend()` returns `MARKETING_SEND` for every kind; opening a kind to the office later is a change
in that one policy class (and a new permission row in `docs/05-roles-permissions.md`).

| Method and URL | Permission | What |
|---|---|---|
| `GET /notification-templates?kind=` | VIEW | list with approval status |
| `GET /notification-templates/{id}` | VIEW | one template |
| `POST /notification-templates` | TEMPLATES | new (name, kind, language, body, smsBody, headerType, mediaKey, buttons, exampleValues) |
| `PUT /notification-templates/{id}` | TEMPLATES | edit → new row, WAITING |
| `PUT /notification-templates/{id}/approval` | TEMPLATES | local/test only (`app.notifications.manual-approval`), else 404: APPROVED / REJECTED + reason |
| `POST /notification-media` | TEMPLATES or SEND | multipart → `{ mediaKey, mediaType, bytes, width, height, seconds }` |
| `POST /notifications/refine` | SEND | AI suggestion (section 7) |
| `POST /notifications` | SEND (kind-checked) | DRAFT + counts |
| `PUT /notifications/{id}` | SEND | change a DRAFT/SCHEDULED |
| `GET /notifications/{id}/recipients` | VIEW | the resolved list (paged), to show "See the 246 families" |
| `POST /notifications/{id}/test` | SEND | one TEST row to the caller's phone |
| `POST /notifications/{id}/send` | SEND | `{ sendNow: true }` or `{ scheduledAt }` |
| `DELETE /notifications/{id}` | SEND | cancel DRAFT/SCHEDULED; a SENDING one cancels only the QUEUED rows |
| `GET /notifications?kind=&status=&from=&to=` | VIEW | list (Home and Reports screens) |
| `GET /notifications/{id}/report` | VIEW | counts by status, by button, by failure reason, read-by-hour |
| `POST /notifications/{id}/resend-failed` | SEND | new QUEUED rows for FAILED ones |
| `GET /message-stops`, `DELETE /message-stops/{guardianId}` | VIEW / SEND | stop list |
| `GET /inbound-messages?handled=false` | VIEW | replies (STOP handled by the server, the rest for a person) |
| `POST /inbound-messages/{id}/handle` | SEND | mark done, note, or `makeEnquiry: true` |
| `POST /webhooks/{provider}/status`, `/inbound`, `/template-status` | none (signature check) | provider callbacks |

Errors (409 unless said): `TEMPLATE_NOT_APPROVED`, `KIND_NOT_ALLOWED_FOR_ROLE` (403), `AUDIENCE_NOT_ALLOWED`,
`NOTIFICATION_NOT_EDITABLE`, `NO_RECIPIENTS`, `MEDIA_TOO_BIG`, `MEDIA_WRONG_TYPE`, `SCHEDULE_IN_PAST`,
`SCHEDULE_IN_QUIET_HOURS` (unless URGENT), `REFINE_NOT_CONFIGURED`, `REFINE_LOST_PLACEHOLDER`.

## 10. Checks before a message goes out (the guard chain)

Nothing is sent "because someone clicked". Every recipient passes a chain of checks **twice**: once when the
dispatcher expands the notification (minutes before sending), and the cheap ones again inside `OutboxWorker`
right before the provider call (seconds before sending). A check that fails does not throw; it records a
`skip_reason` so the report can show "12 skipped: already paid".

```java
public interface RecipientGuard {
    int LAST_SECOND = 90;   // guards from here on run in OutboxWorker, the rest at expand time
    /** Lower runs first. 10 = cheap and general, 50 = per kind, 90 = last-second re-check. */
    int order();
    boolean appliesTo(NotificationKind kind);
    Optional<SkipReason> check(Candidate c, Notification n, GuardContext ctx);   // empty = may go
}
// Candidate = the Recipient + values the guards fill (live fee numbers) + holdUntil (quiet hours).
// GuardContext = now, today, template, policy, and a per-run cache (the stop list is read once per run).
// GuardChain.check(recipient, notification, context) → GuardOutcome(skipReason, values, holdUntil)
// GuardChain.beforeSend(outboxRow) → Optional<SkipReason>   (called by OutboxWorker)
```

Guards are Spring beans, collected into a list, sorted by `order()`. **To add a check: one class.** The list, in
order, with the kind it applies to:

| # | Guard | Kinds | Skips when | Skip reason |
|---|---|---|---|---|
| 10 | `PhoneGuard` | all | no phone, or not `+91[6-9]…` | `NO_PHONE` |
| 11 | `DuplicateGuard` | all | the same phone already has a row for this notification (unique index `(notification_id, phone)` backs it up) | `DUPLICATE` |
| 12 | `PersonActiveGuard` | all | guardian's every child is LEFT/FINISHED; staff `active = false`; enquiry no longer in the chosen stage | `PERSON_INACTIVE` |
| 13 | `StopListGuard` | MARKETING, GREETING | guardian in `message_stop` | `STOPPED` |
| 14 | `WeeklyCapGuard` | MARKETING, GREETING | already 2 marketing/greeting rows to this phone in the last 7 days (`app_setting`) | `WEEKLY_CAP` |
| 15 | `TemplateApprovedGuard` | all | template not APPROVED, and the row is not free text inside the 24-hour window | `TEMPLATE_NOT_APPROVED` |
| 16 | `WindowGuard` | ONE_FAMILY, STAFF free text | last `inbound_message` from this phone older than 24 h | `OUTSIDE_WINDOW` (the row is re-made with the nearest approved template instead of skipped) |
| 20 | `QuietHoursGuard` | all except URGENT | now is inside quiet hours | not a skip: the row gets `next_try_at = end of quiet hours` |
| 50 | `FeeDueGuard` | FEE_REMINDER | see the fee block below | `ALREADY_PAID`, `PROMISED`, `REMINDED_RECENTLY`, `CREDIT_COVERS`, `BELOW_FILTER` |
| 51 | `RouteTodayGuard` | URGENT (ROUTE), ROUTE_CREW | child not on that route today (`transport_enrolment`), or no bus today (holiday), or crew not assigned today | `NOT_ON_ROUTE_TODAY` |
| 52 | `ClassFilterGuard` | NOTICE, URGENT | child's class today is not in the filter (class changed since the draft) | `FILTER_NO_LONGER_MATCHES` |
| 53 | `EnquiryStageGuard` | MARKETING, GREETING | enquiry got ADMITTED since (the family is now a current parent, counted there) | `NOW_ADMITTED` |
| 90 | `LastSecondGuard` | FEE_REMINDER, URGENT | run inside `OutboxWorker`: dues still > 0 right now; URGENT older than 2 h is not sent (same rule bus SMS has) | `ALREADY_PAID`, `TOO_OLD` |

Skip reasons are an enum `SkipReason`. A skipped recipient is written as a `message_outbox` row with
`status = SKIPPED` and `skip_reason`, so the report shows them and the office can see "Chauhan: skipped, paid on
8 Oct". `SKIPPED` is a new status in the check constraint. The worker never picks SKIPPED rows.

### The fee reminder, step by step (what "check the current status" means)

The list is **never** stored. Every time a fee reminder is expanded, and again right before each send:

1. Read `v_fee_month` for the family **now**: every month with `status in (DUE, LATE)` for every ACTIVE child of
   this guardian. Sum = `{Amount due}`, months = `{Months}`, late fee from `session_fee_setting` = `{Late fee}`.
2. `amount == 0` → skip `ALREADY_PAID`. Example: the owner scheduled the reminder on the 9th for the 10th at 10 am;
   Punia family paid on the 10th at 9:30; at 10:00 the dispatcher sees ₹0 and skips them.
3. `family_credit_entry` sum ≥ amount → skip `CREDIT_COVERS`.
4. A receipt for this family today → skip `ALREADY_PAID` (the payment is being saved while we expand).
5. Filter from the audience: `minMonthsLate` (how many months are LATE), `minAmount` → below → `BELOW_FILTER`.
6. An `inbound_message` from this phone flagged `PROMISED` with `promise_date >= today` → skip `PROMISED`
   (the office marks a reply "will pay by Friday" with one click; after Friday the family is back in).
7. A FEE_REMINDER row to this phone in the last `skipRemindedWithinDays` (default 7) → `REMINDED_RECENTLY`.
8. The text is filled with the numbers from step 1 **at this moment**, not from the draft. The draft only holds the
   template and the tone.
9. In `OutboxWorker`, just before the provider call, `LastSecondGuard` runs step 1 and 2 again (one query). If the
   family paid in the 2 minutes between expand and send, the row becomes SKIPPED instead of sent.

The same idea for the other kinds: URGENT re-reads today's route list; NOTICE re-reads the class; MARKETING
re-reads the stop list. The draft stores *the question* ("families with dues, 2+ months"), never *the answer*.

### What the report shows because of this

```
Fee reminder · 10 Oct 10:00
41 families matched when you saved · 33 got it · 8 skipped:
  5 already paid · 2 promised a date · 1 reminded 3 days ago
```

## 9. Where to extend (the point of the shape)

| Want to add | Touch |
|---|---|
| A new check before sending ("skip families with a court case") | one `RecipientGuard` class |
| A new kind ("Exam result") | `NotificationKind` + one `KindPolicy` class + the `check` constraint (new Flyway file) |
| A new audience ("Parents of children who left") | one `RecipientResolver` class + the `AudienceGroup` enum value |
| A new channel (email, app push) | one `ChannelSender` + `MessageChannel` value |
| A new provider | one `ProviderApi` class (unchanged rule from Phase 5) |
| Automatic messages (Phase 12) | `notification_rule` table + `RuleScheduler` that **creates** a `notification` row with `source = RULE`; everything after that is this module |
| Group posting (if ever) | one `ChannelSender` for `GROUP` + an audience `WHATSAPP_GROUP`; nothing else |
| A better inbox | read `inbound_message`; the table is already filled |
| Video re-encoding | one `MediaProcessor` in front of `FileStorage.save` |
