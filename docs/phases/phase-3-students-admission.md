# Phase 3 — Students and admission

## Goal

The office can admit a student with family details, find a student later, and change anything: details, parents' phone numbers, photo, and the bus (start later, change route, stop).

## Screens this makes work

- **6. New admission** (student, family and transport parts; the fees part comes in Phase 7)
- **9. Students**
- **10. One student (edit)** (without the "Fees this year" box until Phase 7)
- **3. Routes and load** now shows real child counts

## Read first

- `docs/03-data-model.md` → Phase 3 tables, with the three bus examples
- `docs/06-api.md` → Students

## Tables

`V3__students.sql`: `student`, `guardian`, `student_guardian`, `transport_enrolment`, `admission_counter`.

## Endpoints

All under `/api/v1`: `/students` (list), `/students/{id}`, `/students/{id}/guardians`, `/students/{id}/transport`, `/students/{id}/photo`, `/students/{id}/history`, `/students/import`, `/admissions`. Methods and permissions are in `docs/06-api.md`.

## Rules

### Admission

1. `POST /admissions` does everything in **one transaction**: student, parents' phones, bus enrolment. If any part fails, nothing is saved.
2. The server makes the admission number: `A-<year>-<running number>`. Example: the 118th admission of 2026 is `A-2026-118`. Lock the `admission_counter` row so two clerks never get the same number.
3. At least one parent phone is needed.
4. The request may carry `enquiryId`. The link is stored in Phase 6; accept and ignore the field now.
   Also: the request may carry `siblingStudentId` ("brother or sister already in this school"). Then the new child is linked to every phone of that brother or sister, and the clerk does not type the parents again.

### Parents' phone numbers

5. One phone number is one `guardian` row. When a phone is typed that already exists, **reuse that row** and add a link.
   Example: Siya is already a student and her mother's phone is saved. Aryan is admitted with the same phone. Result: still one `guardian` row, now with two `student_guardian` links.
6. Adding a phone that is already linked to this child → 409 `PHONE_ALREADY_LINKED`.
7. The last phone of a child cannot be removed → 409 `LAST_GUARDIAN`.
8. Removing a link does not delete the `guardian` row if another child uses it.
9. `sms_enabled` can be switched per link. Example: the grandfather wants no SMS for Siya but wants it for Aryan.

### Bus

10. `PUT /students/{id}/transport` handles all three cases. Read the examples in `docs/03-data-model.md`.
    - **Start:** no open row exists → insert one from `fromDate`.
    - **Change:** an open row exists → set its `to_date = fromDate - 1`, insert the new row.
    - **Stop:** `usesBus = false` → set `to_date = fromDate - 1` on the open row.
11. The stop must belong to the route → 400 `STOP_NOT_ON_ROUTE`.
12. `fromDate` cannot be before the child's `joined_on`.
13. If the route would have more children than seats, **save anyway** and return the warning `ROUTE_FULL`. The school decides; the software only warns.
14. A child can have one open row only (the database index enforces it).

### Editing

15. `PUT /students/{id}` changes details. The admission number never changes.
16. Every change writes an `audit_log` row with a readable summary:
    - "Section changed from B to A"
    - "Phone +91XXXXXX0208 added (Grandfather)"
    - "Bus started: Route 9, Model Town, from 2 Nov 2026"
17. A student is never deleted. `status = LEFT` with `left_on`. Leaving also closes the open bus row.

### Photo

18. Accept JPEG and PNG up to 2 MB. Check the real file type from the first bytes, not only the file name.
19. Save through `PhotoStorage` (a local folder in dev). Store the key in `student.photo_key`.
20. `GET /students/{id}/photo` checks `STUDENTS_VIEW`, then streams the file. There is no public URL.
21. Uploading a new photo removes the old file.

### List

22. `GET /students` is paged. Filters: `q` (name, admission number or phone), `className`, `village`, `routeId`, `bus=YES|NO`. Only ACTIVE students unless `status=LEFT` is asked.
23. Each row has: name, admission number, class, village, bus now ("Route 4 · Jakhal" or null), first parent phone, `hasPhoto`.

### Import of existing students

24. `POST /students/import` takes a CSV with one line per child:
    `name, gender, dob, class, section, village, father_occupation, parent_name, parent_phone, route, stop`
25. It always reports exactly what happened: `{ "created": 284, "errors": [ { "line": 17, "message": "Phone has 9 digits" } ] }`. Lines with errors are skipped; good lines are saved.
26. A `dryRun=true` option checks the file and saves nothing. Use it first.

### Connect to Phase 2

27. Replace the stub `StudentCounts` from Phase 2 with real counts. Now Routes and load, `ROUTE_HAS_STUDENTS` and `STOP_HAS_STUDENTS` work for real.

## Tasks

- [x] 3.1 `V3__students.sql`.
- [x] 3.2 `student` package: entities `Student`, `Guardian`, `StudentGuardian`, `TransportEnrolment`; enums; repositories.
- [x] 3.3 `AdmissionNumberService` (rule 2) with a test that runs 20 admissions at the same time and gets 20 different numbers.
- [x] 3.4 `GuardianService.linkPhone(studentId, name, phone, relation, smsEnabled)` (rules 5, 6).
- [x] 3.5 `TransportEnrolmentService`: `current(studentId, date)`, `start`, `change`, `stop` (rules 10 to 14). Unit test the date logic first.
- [x] 3.6 `AdmissionService` + `POST /admissions` (rules 1 to 4).
- [x] 3.7 `StudentService.update` + `PUT /students/{id}` with audit summaries (rules 15, 16).
- [x] 3.8 Guardian endpoints: add, change, remove (rules 6 to 9).
- [x] 3.9 `PUT /students/{id}/transport` and `GET /students/{id}/transport`.
- [x] 3.10 `PhotoStorage` interface, `LocalFolderPhotoStorage`, the three photo endpoints (rules 18 to 21).
- [x] 3.11 `GET /students` with filters and paging (rules 22, 23).
- [ ] 3.12 `GET /students/{id}` (full profile) and `GET /students/{id}/history`.
- [ ] 3.13 Mark a student as LEFT (rule 17).
- [ ] 3.14 `StudentQueryService.onRoute(routeId, date)`: the "children on route R on day D" query. Phase 4 uses it.
- [ ] 3.15 Real `StudentCounts`; re-run the Phase 2 tests for load board and stops (rule 27).
- [ ] 3.16 CSV import with dry run (rules 24 to 26).
- [ ] 3.17 Extend the dev data loader: about 40 sample students across the routes, some brothers and sisters sharing a phone.

## Tests that must pass

- `admissionSavesStudentParentsAndBusTogether`
- `admissionWithBadStopSavesNothing` (rollback)
- `twentyAdmissionsAtOnceGetTwentyNumbers`
- `samePhoneForBrotherAndSisterIsOneGuardianRow`
- `lastPhoneCannotBeRemoved`
- `removingALinkKeepsGuardianUsedByAnotherChild`
- `busStartedLaterIsNotOnTheRouteBeforeItsDate`
- `routeChangeClosesOldRowTheDayBefore`
- `stoppingBusClosesTheRow`
- `fullRouteGivesWarningButSaves`
- `stopFromAnotherRouteIsRejected`
- `everyChangeAppearsInHistoryWithWhoAndWhen`
- `photoIsNotReachableWithoutLogin` and `...WithoutStudentsView`
- `fileThatIsNotAnImageIsRejected`
- `importDryRunSavesNothing`
- `importSkipsBadLinesAndReportsThem`
- `loadBoardNowCountsRealChildren`
- Permission tests: `TRANSPORT_INCHARGE` can view but not edit; `ATTENDANT` gets 403 on everything here.

## Done when

- You admit a child with no bus. On the student page data you start the bus from a later date. `StudentQueryService.onRoute` does not include the child before that date and includes them from that date.
- You add a grandfather's phone that already belongs to a cousin. The `guardian` table still has one row for that phone.
- You upload a photo and can fetch it only with a token that has `STUDENTS_VIEW`.
- You import the school's real student sheet with `dryRun=true`, fix the reported lines in Excel, then import for real.
- `/check-phase 3` passes.

## Out of scope

- Fees (Phase 7). `POST /admissions` ignores fee fields for now.
- Promotion to the next class each April.
- Attendance in class, marks, documents like birth certificate.
