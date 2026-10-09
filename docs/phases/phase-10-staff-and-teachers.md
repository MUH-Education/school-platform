# Phase 10 — Employees and teachers

## Goal

`staff` stops meaning "transport people" and starts meaning "every employee of the school". The office can add a teacher, fill in the basic employee details (joining day, date of birth, address, emergency phone, ID proof), and the owner alone can see and set a salary.

Do this **after go-live**. The owner's words: "we need to do launch as simple application".

## Screens this makes work

- **11. Vehicles and staff** — gets a type filter, so the transport screen keeps showing only drivers, attendants and helpers
- A new **Employees** screen (teachers included), with one person's details

## Read first

- `docs/03-data-model.md` → Phase 10 tables
- `docs/05-roles-permissions.md` → where `STAFF_SALARY_VIEW` fits
- Decisions B22, B23, B24 and question C11 in `docs/08-decisions.md`

## Tables

`V8__staff_employee.sql`: new columns on `staff`, `staff_type_ck` recreated with `TEACHER`, plus the tables `teacher_profile` and `staff_salary`.

Do not edit `V2__vehicles_staff_routes.sql`. It has already run.

## Endpoints

All under `/api/v1`:

| URL | Permission | Meaning |
|---|---|---|
| `GET /staff?type=TEACHER` | `VEHICLES_VIEW` | The same list as today, with an optional type filter. Several types: `?type=DRIVER&type=HELPER`. |
| `GET /staff/{id}` | `VEHICLES_VIEW` | One person, with `teaching` when the type is TEACHER. Never the salary. |
| `PUT /staff/{id}` | `VEHICLES_EDIT` | Takes the employee details too, and `teaching` for a teacher. |
| `GET /staff/{id}/salary` | `STAFF_SALARY_VIEW` | `{ "monthlySalary": 18500.00, "updatedBy": 1, "updatedAt": "..." }`, or 404 when no salary is saved. |
| `PUT /staff/{id}/salary` | `STAFF_SALARY_EDIT` | `{ "monthlySalary": 18500.00 }` |

`POST /staff` keeps working as it does today. A teacher is created by sending `staffType: "TEACHER"`.

## Rules

1. **A teacher is a `staff` row, not a new entity.** `staff_type = TEACHER`. The shared columns live on `staff`; only teaching extras live in `teacher_profile`.
2. **A teacher can never be put on a bus.** `Duty` maps each duty to one `StaffType` (`Duty.java`), and there is no TEACHER duty. So rule 11 of Phase 2 already answers 409 `WRONG_STAFF_TYPE`. Do not add a TEACHER duty.
3. **A teacher has no licence.** The existing `staff_licence_ck` already allows null for a non-driver. Nothing to change.
4. `teaching` is accepted only when `staffType` is TEACHER. Otherwise 400 `NOT_A_TEACHER`, and nothing is saved.
5. Changing a person's type away from TEACHER deletes the `teacher_profile` row. Changing it to TEACHER creates an empty one.
6. **The salary never appears in `StaffResponse`.** It has its own endpoint, its own record, and its own permission. `OWNER` gets `STAFF_SALARY_VIEW` and `STAFF_SALARY_EDIT`. No other role gets either.
7. `emergency_phone` goes through `PhoneNumbers.normalize()`, like every other phone.
8. Only the **last 4 digits** of an ID proof are stored (`id_proof_last4`). Never the full Aadhaar number. See question C11.
9. The existing `GET /staff` with no `type` keeps returning everybody, so nothing that works today breaks. The React Vehicles and staff screen must start sending `?type=DRIVER&type=ATTENDANT&type=HELPER`.

## Tasks

- [x] 10.1 `V8__staff_employee.sql`: the new `staff` columns, `staff_type_ck` with TEACHER, `teacher_profile`, `staff_salary`.
- [x] 10.2 `StaffType`: add `TEACHER`. Check that `Duty` still compiles and no TEACHER duty appears.
- [x] 10.3 `Staff` entity: the new fields. New `TeacherProfile` entity and `TeacherProfileRepository`.
- [x] 10.4 `StaffResponse` and `UpdateStaffRequest`: the employee details (`details`) and `teaching`. No salary field anywhere.
- [x] 10.5 `StaffService`: save the details, create and delete the teacher profile (rule 5), normalize `emergency_phone`.
- [x] 10.6 `GET /staff?type=` filter, and `GET /staff/{id}`.
- [x] 10.7 `Permission`: `STAFF_SALARY_VIEW`, `STAFF_SALARY_EDIT`. Give both to `OWNER` only in `Role`.
- [x] 10.8 `StaffSalaryService` and the two salary endpoints.
- [x] 10.9 Update `docs/06-api.md` and `docs/05-roles-permissions.md`.

## Tests that must pass

- A teacher is created with `staffType: TEACHER` and no licence → 201.
- A teacher sent to a vehicle as a DRIVER duty → 409 `WRONG_STAFF_TYPE`.
- `teaching` sent for a DRIVER → 400 `NOT_A_TEACHER`, and the driver row is unchanged.
- Changing a teacher to HELPER deletes the `teacher_profile` row.
- `GET /staff` as `TRANSPORT_INCHARGE` returns teachers, and **no response field anywhere contains a salary**.
- `GET /staff/{id}/salary` as `TRANSPORT_INCHARGE` → 403. As `OWNER` → 200.
- `GET /staff/{id}/salary` with no token → 401.
- `GET /staff?type=TEACHER` returns only teachers.
- The Phase 2 assignment tests still pass unchanged.

## Done when

- The office can add a teacher with qualification, subjects and class, and see them in a list filtered by type.
- A teacher cannot be assigned to any vehicle, by any request.
- The transport incharge can see the staff list but gets 403 on every salary URL.
- `./gradlew test` is green, including every Phase 2 test.

## Out of scope

- **Teacher login and class attendance.** Owner's answer: plan it, do not build it. When it comes, it is a `staff` row plus an `app_user` row linked by `staff_id` and a `TEACHER` role — the same door the attendant already uses. No change to `app_user`.
- **Scanned papers** (Aadhaar copy, licence scan, police verification). Owner's answer: future. It will be a `staff_document` child table, copying the `vehicle_document` pattern, and will reuse the Phase 3 photo storage.
- **Exam papers and marks.** A separate module, much later.
- Salary history, raises, payslips, attendance of employees, leave.
- Full Aadhaar numbers (question C11).
- Visitors. That is its own feature and its own phase (decision B23).
