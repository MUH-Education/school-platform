# Phase 6 — Enquiries

## Goal

Every parent who asks about admission is saved once, moves through clear stages, and is never forgotten.

This phase needs only Phase 1. You can build it any time after Phase 1. The link "enquiry → admitted student" needs Phase 3.

## Screens this makes work

- **4. Enquiry list**
- **5. Add an enquiry**
- **6. New admission** → the blue box "Started from the enquiry of ..."

## Read first

- `docs/03-data-model.md` → Phase 6 tables
- `docs/06-api.md` → Enquiries

## Tables

`V6__enquiries.sql`: `enquiry`, `enquiry_follow_up`.

## Endpoints

`GET /enquiries`, `GET /enquiries/summary`, `POST /enquiries`, `GET /enquiries/{id}`, `PUT /enquiries/{id}`, `POST /enquiries/{id}/follow-ups`, `POST /enquiries/{id}/status`.

## Rules

1. Needed fields: parent name, phone, village, class wanted, source. Everything else is optional.
   Example: a parent calls for two minutes. The clerk types 5 fields and saves. The rest is added at the visit.
2. A new enquiry starts as `NEW`.
3. **Stages:** `NEW → CONTACTED → VISITED → APPLIED → ADMITTED`. And `LOST` from any stage.
   - Moving forward by hand is allowed between NEW, CONTACTED, VISITED and APPLIED, also skipping one. Example: a walk-in parent goes from NEW straight to VISITED.
   - `LOST` needs `lostReason` → else 400.
   - `ADMITTED` cannot be set by hand. It is set only by `POST /admissions` with `enquiryId` (rule 8).
   - A LOST enquiry can be opened again (back to CONTACTED).
4. **Follow-up.** Each call or visit is one `enquiry_follow_up` row with a note. It may set the next date. The enquiry's `next_follow_up_on` is always the newest next date.
5. **Overdue** = `next_follow_up_on` is before today, and the status is not ADMITTED or LOST. It is calculated, not stored.
   Example on 7 Oct: next date 5 Oct, status CONTACTED → overdue since 5 Oct.
6. **Same phone twice.** If an open enquiry with the same phone and class exists, answer 409 `ENQUIRY_EXISTS` with the id of the old one. The clerk opens that one instead.
7. **Referral.** When source is `REFERRAL`, `referredBy` (a name) is needed. If the typed phone or name matches a known guardian, the React app may send `referredByGuardianId` too.
8. **Admission link.** Change `AdmissionService` from Phase 3: when `enquiryId` is given, set that enquiry to `ADMITTED` and fill `admitted_student_id`, in the same transaction.
9. **Summary** (`GET /enquiries/summary`): count per stage, total, overdue count, and admitted ÷ total as a percentage.
   Example: 29 enquiries, 4 admitted → 14%.
10. **List** is paged, newest first. Filters: `status`, `village`, `source`, `overdue=true`, `q` (name or phone).

## Tasks

- [x] 6.1 `V6__enquiries.sql`.
- [x] 6.2 `enquiry` package: entities, enums `EnquirySource`, `EnquiryStatus`, repositories.
- [ ] 6.3 `EnquiryService.create` and `update` (rules 1, 2, 6, 7).
- [ ] 6.4 `EnquiryService.changeStatus` (rule 3). Unit test the allowed moves as a table.
- [ ] 6.5 Follow-ups (rule 4).
- [ ] 6.6 List with filters, overdue flag on each row (rules 5, 10).
- [ ] 6.7 Summary (rule 9).
- [ ] 6.8 Link from admission (rule 8). Add the test to the Phase 3 admission tests.
- [ ] 6.9 `GET /enquiries/{id}/prefill`: returns the fields the admission form can copy (parent name, phone, village, class, child name). The New admission screen calls it.
- [ ] 6.10 Audit rows for create, status change and follow-up.
- [ ] 6.11 Dev data: 29 sample enquiries across the stages.

## Tests that must pass

- `enquiryCanBeSavedWithFiveFields`
- `lostNeedsAReason`
- `admittedCannotBeSetByHand`
- `walkInCanSkipToVisited`
- `lostEnquiryCanBeReopened`
- `followUpMovesTheNextDate`
- `enquiryWithPastDateIsOverdue` (fixed `Clock`)
- `admittedOrLostIsNeverOverdue`
- `samePhoneAndClassTwiceGives409`
- `referralNeedsAName`
- `admissionWithEnquiryIdMarksItAdmitted`
- `summaryCountsEveryStage`
- Permission tests: `TRANSPORT_INCHARGE` and `ATTENDANT` get 403.

## Done when

- You can answer from the API, without a spreadsheet: how many enquiries, what % admitted, which are overdue, how many per village.
- An admission made from an enquiry closes that enquiry.
- `/check-phase 6` passes.

## Out of scope

- Sending SMS to a filtered list of enquiries.
- The referral fee credit itself (that is a discount in Phase 7).
- A public "Apply online" form on the website.
