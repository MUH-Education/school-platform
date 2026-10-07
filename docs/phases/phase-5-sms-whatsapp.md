# Phase 5 — SMS and WhatsApp

## Goal

Parents get the right Hindi SMS for each tap. Staff get their login code on WhatsApp, or by SMS if WhatsApp fails.

## Screens this makes work

- **2. One bus** → the "SMS to parent" column
- **Messages** (menu item; the screen is not drawn yet, the API is ready for it)
- Real login codes for the web app and **M1. Login**

## Read first

- `docs/07-messaging.md` (all of it)
- `docs/04-login-otp-jwt.md` → Step B

## Before you start (not code)

- Question C1 must be answered: which provider, and DLT registration is done or in progress.
- You have the provider's API key and their document for "send SMS with a DLT template" and "send a WhatsApp authentication template".

You can build tasks 5.1 to 5.9 without the provider. Only 5.10 to 5.12 need real keys.

## Tables

`V5__messaging.sql`: `message_template` (with the 8 Hindi texts from `docs/07-messaging.md`) and `message_outbox`.

## Endpoints

`GET /messages`, `GET /messages/summary`, `GET /message-templates`, `PUT /message-templates/{code}`.

## Rules

1. **Class rule.** `SmsPolicy.allows(className, eventType)` follows the table in `docs/07-messaging.md`.
   Example: Class 3 → all four. Class 9 → only `REACHED_SCHOOL` and `BOARDED_EVENING`. Class 11 → none.
2. **Queue in the same transaction.** The real `BoardingNotifier` writes `message_outbox` rows inside the transaction that saves the tap. If the tap is rolled back, no SMS exists.
3. **One row per parent phone** that has `sms_enabled = true` for this child.
4. **No SMS** when: outcome is not `DONE`; the class rule says no; the tap is for a day that is not today; or a boarding SMS for this parent, child, day and event already exists.
5. **Boy or girl text.** Template code = event + `_M` or `_F` from `student.gender`.
6. `{name}` = the first word of the child's name. `{time}` = tap time in the school zone as `h:mm`, for example `7:42`.
7. The final text must be 70 characters or less.
8. **The worker.** Every 5 seconds: take up to 50 `QUEUED` rows, oldest first, with `for update skip locked`. Send each. Save the result.
9. **Retry.** Failure → `attempts + 1`, stay `QUEUED`. After 3 failures → `FAILED`.
10. **Too old.** A boarding SMS queued more than 2 hours ago is not sent. Mark `FAILED`, error "too old".
11. **Test mode.** With `sms-provider: log`, the row becomes `TEST_ONLY` and the text goes to the log. Nothing leaves the server.
12. **Phone privacy.** `GET /messages` shows phones as `+91XXXXXX4321`.
13. **OTP.** `OtpDeliveryService` tries WhatsApp, then SMS. Each call has a 5 second timeout.
14. In the `prod` profile the app refuses to start when the SMS provider or the only OTP channel is `log`.

## Tasks

- [x] 5.1 `V5__messaging.sql` with the 8 template rows.
- [x] 5.2 `SmsPolicy` (pure Java) and `SmsPolicyTest` for every class from Nursery to 12.
- [x] 5.3 `SmsTextBuilder` (pure Java): template + child + time → text. Test the 70 character limit with a long name.
- [x] 5.4 `MessageOutbox` and `MessageTemplate` entities and repositories.
- [x] 5.5 `OutboxBoardingNotifier` replaces `NoOpBoardingNotifier` (rules 2 to 6).
- [x] 5.6 `SmsSender` interface and `LogSmsSender`.
- [x] 5.7 `OutboxWorker` with `@Scheduled` (rules 8 to 11). Make it possible to call `runOnce()` from a test.
- [x] 5.8 `GET /messages`, `GET /messages/summary`, template endpoints (rule 12).
- [x] 5.9 `GET /bus-status/routes/{routeId}` now includes, per child and event, the SMS state: sent at, not for this class, or failed.
- [ ] 5.10 The real SMS sender class for the chosen provider. Read the provider's document. Keep all provider details inside this one class.
- [ ] 5.11 `WhatsAppOtpSender` and `SmsOtpSender` for the chosen provider.
- [x] 5.12 Start-up checks for `prod` (rule 14).
- [ ] 5.13 One manual test with real keys: log in on your own phone by WhatsApp; tap a test child whose parent phone is yours; receive the Hindi SMS.

## Tests that must pass

- `SmsPolicyTest`: all 15 classes × 4 events.
- `longNameStillFitsIn70Characters`
- `girlGetsTheGirlText`
- `tapCreatesOneOutboxRowPerParentPhone`
- `class11TapCreatesNoRow`
- `class9MorningBoardingCreatesNoRowButReachedSchoolDoes`
- `absentCreatesNoRow`
- `yesterdaysLateTapCreatesNoRow`
- `sameTapThreeTimesCreatesOneRow`
- `undoThenTapAgainDoesNotSendASecondSms`
- `parentWithSmsOffGetsNothing`
- `rolledBackTapLeavesNoOutboxRow`
- `workerMarksSentAndStoresProviderId`
- `workerRetriesThreeTimesThenFails`
- `messageOlderThanTwoHoursIsNotSent`
- `twoWorkersNeverSendTheSameRow`
- `otpFallsBackToSmsWhenWhatsAppFails`
- `prodRefusesToStartWithLogSender`
- `phonesAreMaskedInTheMessagesList`

## Done when

- In dev: tap a Class 3 child with two parent phones → two rows, both `TEST_ONLY`, text in Hindi in the log.
- Tap a Class 11 child → no row.
- With real keys: task 5.13 works on your own phone.
- `/check-phase 5` passes.

After this phase, the transport system is complete: Phases 0 to 5.

## Out of scope

- Messages typed by the office to many parents (bulk SMS). The table is ready for it (`purpose`), the feature is later.
- WhatsApp messages to parents.
- Delivery reports from the provider ("delivered to handset"). `SENT` means the provider accepted it.
