# Phase 11 — Notification module: templates, notifications, media, AI refine

**Replaces `phase-11-marketing.md`** (keep the old file for history, mark it "superseded 21 Oct 2026" at the top).

## Goal

The office writes a message once (notice, urgent, marketing, greeting, fee reminder, one family, staff), attaches a
poster, video or PDF if it wants, picks who gets it, sends now or later, and sees who got it, read it and tapped.
Every send is one `notification` row expanded into `message_outbox` rows by a poller. No queue system, no cost
numbers, no approval step, no group posting.

Needs Phase 5 (outbox, worker, `ProviderApi`) and Phase 3 (students, guardians). Does not need Phase 9.

## Screens this makes work

Notification canvas: Home, What to send, Write (all 7 kinds), Who gets it, When and send, Report, Templates,
New template, Settings (stop list part only). Inbox stays the provider's own inbox for now.

## Read first

- `docs/10-notifications.md` — the whole design, read all of it
- `docs/07-messaging.md` — the outbox and worker you are extending
- `docs/02-architecture.md` — layers, naming, `impl` packages
- `docs/08-decisions.md` → B17 (files), B26 (no Redis), C1 (provider still open)

## Tables

`V14__notifications.sql` (V13 is taken). Everything in `docs/10-notifications.md` sections 4 and 5:

- stretch `message_template` (kind, header_type, media_key, sms_body, example_values, provider_template_name,
  provider_language, rejection_reason, replaces_id, created_by); existing rows → `kind = 'SYSTEM'`
- new `message_template_button`
- new `notification`
- stretch `message_outbox` (notification_id, read_at, button_action, media_key, values jsonb, new purposes)
- new `message_stop`, `inbound_message` (with `promised_until date`)
- `message_outbox`: `skip_reason`, status `SKIPPED`, unique `(notification_id, phone)`
- `app_setting` rows: `notifications.quiet_from = '21:00'`, `notifications.quiet_to = '08:00'`,
  `notifications.max_marketing_per_family_per_week = 2`

Also: `V15__file_storage.sql` only if renaming `photo_key` columns is wanted — **it is not**; keep column names,
rename only the Java interface.

## Rules (each with an example)

1. **A notification is one row; the people are rows in `message_outbox`.** Example: "Diwali greetings" to 247
   people = 1 `notification` row + 247 `message_outbox` rows with `notification_id = 17`.
2. **Rows are written by `NotificationDispatcher`, never by the controller.** `POST /send` only changes the
   status. The dispatcher runs every 60 s, claims with `for update skip locked`, expands, sets `expanded_at`.
   Example: the owner clicks Send at 4:15:10 pm; the request answers in 50 ms; rows exist by 4:16; the worker
   starts sending them 5 s later.
3. **The list is made at expand time.** Example: Malik family taps "Stop messages" at 6:29 pm; the 6:30 pm
   notification does not include them.
4. **One message per family.** Punia family (Aryan 3 B, Siya 11 B) gets one WhatsApp with
   `{Child name} = "Aryan और Siya"`.
5. **Distinct by phone across groups.** An attendant whose child studies here, with "parents" and "transport
   staff" both ticked, gets it once.
6. **The stop list blocks only kinds whose policy says so** (MARKETING, GREETING). A family on the stop list still
   gets a NOTICE, an URGENT, a FEE_REMINDER, bus SMS and receipts.
7. **The kind decides who may send.** `KindPolicy.whoMaySend()` is checked in the service, not only in
   `@PreAuthorize`. **For now every kind returns `MARKETING_SEND`** (owner only; no new permissions or roles,
   21 Oct 2026). Example: Neelam (OFFICE_ADMIN) calls `POST /notifications` → 403. Later, opening NOTICE to the
   office = one policy class + one permission.
8. **URGENT ignores quiet hours and goes on both channels.** Everyone gets WhatsApp and SMS. Any other kind
   scheduled inside quiet hours is refused with `SCHEDULE_IN_QUIET_HOURS`.
9. **A template must be APPROVED to be used**, except ONE_FAMILY and STAFF free text inside the 24-hour window
   (the person wrote to the school number in the last 24 h, checked in `inbound_message`).
10. **Media is ours first.** `POST /notification-media` saves to `FileStorage` and answers a key. At expand time
    `MediaPublisher` uploads once to the provider and caches `provider_media_id` on the notification. Re-upload
    after 25 days. Limits: image 5 MB, video 16 MB MP4, PDF 5 MB, audio 16 MB.
11. **AI refine never changes anything by itself.** It answers a suggestion; a lost `{box}` is a 409. Off when the
    key is missing. The system prompt is a resource file.
12. **Test send** writes one row, `purpose = TEST`, to the caller's own phone, outside `recipient_count`.
13. **Cancel.** DRAFT/SCHEDULED → CANCELLED, nothing sent. SENDING → the QUEUED rows become CANCELLED, SENT ones
    stay; the notification becomes CANCELLED with a note.
14. **Report = group by.** `GET /report` reads only `message_outbox` rows of that notification. No report table.
15. **The dispatcher closes a notification**: SENDING with zero QUEUED rows → SENT, `finished_at`. If every row
    FAILED → FAILED.
16. **Webhooks are idempotent.** `provider_ref` is unique on `inbound_message`; a status callback that arrives twice
    changes nothing the second time. Signature checked with `APP_PROVIDER_WEBHOOK_SECRET`.
17. **STOP handling.** Inbound text equal to "stop" (any case, trimmed, Hindi "बंद" too) or button
    `STOP_MESSAGES` → `message_stop` row (if not there) + `inbound_message.handled = true`. Audit row.
18. **Audit.** Templates, notifications (create, send, cancel), stop list changes → `audit_log`, masked phones.
19. **Every recipient passes the guard chain, twice** (`docs/10-notifications.md` section 10): at expand time and,
    for the cheap ones, again inside `OutboxWorker` just before the provider call. A failed check makes a
    `SKIPPED` row with a `skip_reason`, never an exception. Example: fee reminder expanded at 10:00, Punia paid at
    9:30 → row SKIPPED `ALREADY_PAID`; Chauhan paid at 10:01 while the worker was busy → `LastSecondGuard` skips
    him too.
20. **The fee reminder reads the dues at send time, never from the draft.** Amount, months and late fee come from
    `v_fee_month` at expand time; the draft holds only the template, the tone and the filter. Promised families
    (`inbound_message.promised_until >= today`) and families reminded in the last 7 days are skipped.
21. **Nothing about money.** No price, no cost, no budget anywhere in code, DTOs or settings.

## Tasks (in build order, one per Claude Code session is fine)

- [x] 11.1 `V14__notifications.sql` + `MigrationV14Test` (every new table, every check, the unique on
      `message_stop.guardian_id`).
- [x] 11.2 Enums: `NotificationKind`, `NotificationStatus`, `AudienceGroup`, `RecipientType`, `MediaFileType`,
      `TemplateKind`, `HeaderType`, `ButtonType`, `ButtonAction`, `StopReason`, `RefineAction`. **No new
      `Permission` values and no role change** — endpoints use `MARKETING_VIEW` / `MARKETING_EDIT` /
      `MARKETING_SEND` (owner only).
- [x] 11.3 Entities + repositories: `Notification` (with `Audience` record mapped as JSON), `MessageTemplate`
      (new fields), `MessageTemplateButton`, `MessageStop`, `InboundMessage`; `MessageOutbox` new fields.
      `NotificationRepository.claimDue(limit)` and `findSendingWithoutExpansion` with
      `for update skip locked`.
- [x] 11.4 `service.storage.FileStorage` (rename of `PhotoStorage`, same two impls, same folder), `MediaRules`
      (type + size), `NotificationMediaService.save(MultipartFile)`.
- [x] 11.5 `service.notification.KindPolicy` + 7 policy classes in `impl` + `KindPolicies` (map by kind).
- [x] 11.6 `RecipientResolver` + resolvers in `impl`: `CurrentParentsResolver`, `RouteResolver`,
      `EnquiryResolver` (open/lost), `FamiliesWithDuesResolver`, `StaffResolver` (all / drivers / attendants /
      teachers), `RouteCrewResolver`, `OneFamilyResolver`, `OneStaffResolver`.
      `AudienceService.resolve(kind, audience)` does union → distinct → stop list → counts.
- [x] 11.6b `RecipientGuard` + `SkipReason` + the 13 guard classes in `impl` (section 10), `GuardChain` sorted by
      `order()`, used by the dispatcher; `LastSecondGuard` wired into `OutboxWorker`. `inbound_message` gets
      `promised_until date`; `POST /inbound-messages/{id}/handle` accepts `promisedUntil`.
- [x] 11.7 `NotificationTemplateService` (create, edit-as-new-row, approval in dev/test, auto STOP button).
- [x] 11.8 `NotificationService`: create (DRAFT + counts), update, test, send (now/later), cancel, list, get,
      recipients page.
- [ ] 11.9 `NotificationDispatcher` (`runOnce()`; expand + close) and `NotificationDispatchScheduler`
      (every 60 s, `app.notifications.dispatcher-enabled`, off in test).
- [ ] 11.10 `ChannelSender` + `SmsChannelSender` (wraps `SmsSender`) + `WhatsAppChannelSender`
      (+ `LogWhatsAppChannelSender`); `OutboxWorker` routes by channel; `values` jsonb on the row; SMS fallback
      for `NOT_ON_WHATSAPP`.
- [ ] 11.11 `MediaPublisher` + `ProviderMediaPublisher` + `LogMediaPublisher`; `ProviderApi.uploadMedia(...)`
      added to the interface (javadoc only, no real impl — C1).
- [ ] 11.12 `TextRefiner` + `ClaudeTextRefiner` + `LogTextRefiner`; `prompts/refine.txt`; placeholder check;
      `AiProperties` (`app.ai.api-key`, `model`, `timeout`); `POST /notifications/refine`.
- [ ] 11.13 Webhooks: `WebhookController` (`/webhooks/{provider}/status|inbound|template-status`), signature
      check, `InboundMessageService` (STOP, button actions, `BOOK_VISIT` → `EnquiryService`), status updates on
      the outbox row.
- [ ] 11.14 Report: `NotificationReportService` (by status, by button, by problem, read-by-hour),
      `resend-failed`.
- [ ] 11.15 Stop list endpoints, inbound list + handle.
- [ ] 11.16 Audit rows, `docs/06-api.md` Notifications section, `docs/03-data-model.md` regenerated.
- [ ] 11.17 Dev data (`DevDataLoader`): 3 NOTICE templates, 1 MARKETING approved with a poster, 1 WAITING,
      1 REJECTED with a reason, 1 GREETING with a video key, 1 FEE_REMINDER per tone, 1 STAFF; one SENT
      notification with 20 outbox rows in mixed statuses; 3 stop rows; 4 inbound rows.
- [ ] 11.18 Postman collection updated.

## Tests that must pass

- `migrationV14CreatesEverything`
- `oneRowPerFamilyNotPerChild`
- `familyInTwoGroupsCountedOnce`
- `stopListBlocksMarketingAndGreetingOnly`
- `noticeAndUrgentIgnoreStopList`
- `urgentGoesOnBothChannelsAndIgnoresQuietHours`
- `otherKindsRefuseQuietHoursSchedule`
- `nonOwnerCannotSendAnyKind` (whoMaySend = `MARKETING_SEND` for all 7 kinds)
- `templateMustBeApproved` / `freeTextAllowedInside24hWindow`
- `sendNowOnlyChangesStatus_dispatcherWritesRows`
- `dispatcherExpandsScheduledWhenDue_once` (two `runOnce()` calls, rows written once)
- `dispatcherClosesNotificationWhenNoQueuedLeft`
- `cancelScheduledWritesNothing` / `cancelSendingCancelsOnlyQueuedRows`
- `testSendGoesToCallerOnlyAndIsNotCounted`
- `mediaOverLimitRefused` / `mediaPublishedOncePerNotification`
- `outboxWorkerRoutesWhatsAppRowsToWhatsAppSender` (bus SMS tests unchanged and green)
- `refineKeepsPlaceholdersOrFails` / `refineOffWithoutKey`
- `inboundStopAddsStopRowOnce` / `statusWebhookIsIdempotent`
- `reportCountsEqualOutboxRows`
- `feeReminderSkipsFamilyThatPaidAfterScheduling`
- `feeReminderSkipsPromisedFamilyUntilPromiseDate`
- `feeReminderAmountComesFromLiveDuesNotDraft`
- `lastSecondGuardSkipsPaymentBetweenExpandAndSend`
- `urgentSkipsChildNotOnRouteToday`
- `weeklyCapSkipsThirdMarketingInSevenDays`
- `skippedRowsAppearInReportWithReason`
- Security: every endpoint 401 without token, 403 for a role without the permission (`EndpointSecurityTest`
  table).

## Done when

- In `dev`: make a NOTICE template, approve it by hand, send "PTM on Saturday" to Class 10 → 17 rows in the
  console log, one per family. Send "Diwali greetings" (video key) to all parents + staff → one `LogMediaPublisher`
  line, then one log line per person, stop-listed families missing.
- `POST /notifications/refine` with `SHORTER` returns a shorter text with every `{box}` intact (with a key) or 409
  `REFINE_NOT_CONFIGURED` (without).
- A fake inbound webhook with text "STOP" adds the family to `message_stop`; the next marketing send skips them;
  a notice still reaches them.
- `/report` numbers equal `select status, count(*) from message_outbox where notification_id = ?`.
- `./gradlew build` green; no money word (`cost`, `price`, `paise`, `budget`) in `service.notification`.

## Out of scope (do not build)

- Cost, budget, approval workflow, repeat on the send screen.
- Posting into WhatsApp groups (owner does it by hand).
- Automatic rules (`notification_rule`, birthday, 10th-of-month reminder) → Phase 12.
- Our own inbox UI beyond the two inbound endpoints (provider inbox is used).
- Video re-encoding on the server.
- A real `ProviderApi` implementation (C1 still open). `Log…` classes stand in everywhere.
