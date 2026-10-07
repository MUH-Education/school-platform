# Phase 7 — Fees

## Goal

At admission the office records what a family will pay. Later it records each payment. The system always knows how much is pending and whether the family is on time, delayed or defaulted.

## Stop and check first

Answer question **C2** in `docs/08-decisions.md` before building: does the accountant already keep fees in other software? If yes, decide who types payments here. Building this phase and then not using it is two weeks lost.

## Screens this makes work

- **6. New admission** → part 4 "Fees" and the "Fee summary" box
- **10. One student** → "Fees this year" and "Record a payment"
- Phase 8 (Analytics) reads everything from here

## Read first

- `docs/03-data-model.md` → Phase 7 tables and "How fee status is calculated"
- `docs/06-api.md` → Fees

## Tables

`V7__fees.sql`: `academic_session`, `class_fee`, `fee_plan`, `fee_due`, `fee_payment`, `receipt_counter`. Insert the current session (`2026-27`, 1 Apr 2026 to 31 Mar 2027).

## Endpoints

`GET/POST /sessions`, `GET/PUT /sessions/{id}/class-fees`, `GET /students/{id}/fees`, `PUT /students/{id}/fee-plan`, `POST /students/{id}/payments`, `POST /students/{id}/payment-corrections`, `GET /payments`.

## Rules

### Sessions and class fees

1. Exactly one session is current.
2. `class_fee` holds the standard school fee for each class in a session. The admission form reads it to fill the fee field. The clerk may change the number for one child.

### Fee plan

3. One plan per child per session.
4. A plan has: school fee for the year, bus fee for the year (0 if no bus), discount, discount reason, and how the family pays (MONTHLY, QUARTERLY, YEARLY).
5. The discount is taken off the school fee. A discount above 0 needs a reason.
6. **Dues are made from the plan.**
   - Standard due dates in a session: YEARLY → 1 Apr. QUARTERLY → 1 Apr, 1 Jul, 1 Oct, 1 Jan. MONTHLY → the 1st of each month, April to March.
   - A child who joins mid-year: the first due date is the joining date, then the standard dates after it.
   - Split each fee head equally over its due dates. Round to whole rupees. The last due takes the rest, so the total is exact.

   Example A: school fee ₹30,000, bus fee ₹8,800, QUARTERLY, from 1 Apr.
   SCHOOL: 4 × ₹7,500. BUS: 4 × ₹2,200. Each quarter the family owes ₹9,700.

   Example B: a child joins on 2 Nov, school fee for the rest of the year ₹12,000, QUARTERLY.
   Due dates: 2 Nov and 1 Jan. SCHOOL: 2 × ₹6,000.
7. **Changing a plan** deletes that plan's dues and makes them again. Payments are never touched.
8. **Bus starts later** (`PUT /students/{id}/transport` with `busFee`): add that amount to the plan's `bus_fee` and add BUS dues from the start date. Example: bus starts 2 Nov, ₹4,000, QUARTERLY → BUS dues ₹2,000 on 2 Nov and ₹2,000 on 1 Jan.

### Payments

9. A payment has: fee head, amount, date, mode (CASH, UPI, BANK_TRANSFER, CHEQUE), optional note.
10. One receipt can cover school and bus. Then it is two rows with the same receipt number.
    Example: the family pays ₹9,700 → row SCHOOL ₹7,500 and row BUS ₹2,200, both `R-2026-0412`.
11. The server makes the receipt number: `R-<session start year>-<4 digits>`. Lock `receipt_counter` like the admission counter.
12. A payment cannot be more than what is still unpaid for that head in the session → 409 `PAYMENT_TOO_LARGE`. Paying future dues early is fine.
13. Payments are never changed or deleted. Only the owner can add a correction: `POST /students/{id}/payment-corrections`, permission `FEES_CORRECT`, a row with a negative amount and a note. Every payment and correction writes an `audit_log` row.
14. **The system never handles card or UPI secrets.** It only records "₹9,700 received by UPI". No card numbers, no UPI PIN, anywhere.

### Status

15. Status is calculated by `FeeStatusCalculator` (pure Java) exactly as written in `docs/03-data-model.md`: payments cover dues oldest first; then ON_TIME, DELAYED or DEFAULTED from the grace days and defaulted days in `app_setting`.
16. `GET /students/{id}/fees` returns: the plan, every due with how much of it is covered, every payment, next due date and amount, status per head, overall status, and two totals:
    - `pendingNow` = dues up to today that are not paid. Example on 7 Oct: ₹7,500.
    - `remainingThisYear` = all dues of the session that are not paid. Example: ₹15,000.

### Admission

17. Extend `POST /admissions`: it now accepts the plan and an optional first payment. Still one transaction.
    Example: school ₹30,000, bus ₹8,800, QUARTERLY, first payment ₹9,700 by UPI → the answer includes the receipt number and "still to pay ₹29,100".

## Tasks

- [x] 7.1 `V7__fees.sql`.
- [x] 7.2 `fee` package: entities, enums, repositories.
- [x] 7.3 Sessions and class fees endpoints (rules 1, 2).
- [x] 7.4 `DueScheduleBuilder` (pure Java): plan + start date → list of dues (rule 6). Test examples A and B first.
- [x] 7.5 `FeePlanService.save` (rules 3 to 5, 7).
- [x] 7.6 `ReceiptNumberService` (rule 11).
- [x] 7.7 `PaymentService.record` and `PaymentService.correct` (rules 9, 10, 12, 13). Add `FEES_CORRECT` to the `Permission` enum, owner only, and to `RolePermissionMatrixTest`.
- [x] 7.8 `FeeStatusCalculator` (pure Java) with the examples from `docs/03-data-model.md` (rule 15).
- [ ] 7.9 `GET /students/{id}/fees` (rule 16).
- [x] 7.10 Hook into transport change (rule 8).
- [x] 7.11 Extend admission (rule 17).
- [x] 7.12 `GET /payments` with date filter and paging.
- [x] 7.12a Add `feeStatus` to each row of `GET /students` (the "Fee" column of the Students screen).
- [x] 7.13 Dev data: plans for the sample students, with a mix of on time, delayed and defaulted.

## Tests that must pass

- `quarterlyPlanMakesFourEqualDues`
- `monthlyPlanMakesTwelveDuesAndLastOneTakesTheRest`
- `midYearJoinStartsOnJoiningDate`
- `discountWithoutReasonIsRejected`
- `changingPlanKeepsPayments`
- `oneReceiptCanCoverSchoolAndBus`
- `twentyPaymentsAtOnceGetTwentyReceiptNumbers`
- `paymentLargerThanPendingIsRejected`
- `payingNextQuarterEarlyIsAllowed`
- `sixDaysLateIsStillOnTime` (grace 10)
- `thirtyDaysLateIsDelayed`
- `seventyDaysLateIsDefaulted`
- `paymentsCoverOldestDueFirst`
- `busStartedInNovemberAddsBusDues`
- `admissionWithFirstPaymentReturnsReceipt`
- `officeAdminCannotAddCorrection` → 403
- Permission tests: `TRANSPORT_INCHARGE` gets 403 on all fee URLs.

## Done when

- You admit a child on 1 April with ₹30,000 + ₹8,800 quarterly and ₹9,700 paid. `GET /students/{id}/fees` shows `pendingNow` ₹0, `remainingThisYear` ₹29,100 and next due ₹9,700 on 1 July.
- You set the clock to 15 August without a second payment. Status is DELAYED.
- `/check-phase 7` passes.

## Out of scope

- Online payment, payment links, a payment company.
- Printing a receipt as PDF.
- Fee reminders by SMS.
- Late fines, transport fee by distance, fee for uniforms or books.
- Accounting: ledgers, GST, yearly books. That stays with the accountant.
