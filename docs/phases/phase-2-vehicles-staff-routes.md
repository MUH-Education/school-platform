# Phase 2 — Vehicles, staff, routes

## Goal

The office can add vehicles with their papers, add drivers, attendants and helpers, change who is on which vehicle (with dates), and build routes with ordered stops.

## Screens this makes work

- **11. Vehicles and staff**
- **12. One vehicle (Van 4)**
- **3. Routes and load** (the child counts are 0 until Phase 3 adds students)

## Read first

- `docs/03-data-model.md` → Phase 2 tables, and the assignment rule
- `docs/06-api.md` → Vehicles and staff, Routes

## Tables

`V2__vehicles_staff_routes.sql`: `vehicle`, `vehicle_document`, `staff`, `vehicle_assignment`, `route`, `route_stop`. Also add the foreign key `app_user.staff_id → staff(id)`.

## Endpoints

All under `/api/v1`: `/vehicles`, `/vehicles/{id}`, `/vehicles/{id}/documents`, `/vehicles/{id}/assignments`, `/vehicles/attention`, `/staff`, `/staff/{id}`, `/routes`, `/routes/{id}`, `/routes/{id}/stops`, `/routes/load-board`. Methods and permissions are in `docs/06-api.md`.

## Rules

### Vehicles

1. `name` and `registration_no` are unique. Compare without caring about capital letters or spaces. `hr 23 a 1104` and `HR23A1104` are the same.
2. A vehicle that runs an active route cannot be turned off → 409 `VEHICLE_IN_USE`.
3. Paper status is calculated from `valid_till` and today:
   - before today → `ENDED`
   - today to today + 30 days → `ENDING_SOON`
   - later → `VALID`
   - no date saved → `MISSING`

   Example on 7 Oct: insurance valid till 28 Oct → `ENDING_SOON`, "in 21 days".

### Staff

4. `licence_no` and `licence_valid_till` are needed for a DRIVER, and ignored for others.
5. A staff member who is on a vehicle today or later cannot be turned off → 409 `STAFF_ASSIGNED`.

### Assignments (the important part)

6. To change a person, the request gives: duty, staff, from date, and for a temporary change a to date.
7. **Permanent change.** Close the old permanent row (`to_date = fromDate - 1`). Insert the new row with `to_date` null.
   Example: from 1 Nov, Surender drives Van 4 instead of Jagdish. Jagdish's row gets `to_date = 31 Oct`.
8. **Temporary change.** Do not touch the permanent row. Insert one row with `temporary = true` and both dates.
   Example: Jagdish is on leave 12 to 16 Oct. Add Surender, 12 to 16 Oct, temporary. On 17 Oct Jagdish drives again without any more work.
9. "Who is on the vehicle on day D": the temporary row covering D wins. Else the permanent row covering D.
10. One person cannot be on two vehicles on the same day → 409 `STAFF_BUSY`, and the message names the other vehicle.
11. The duty must fit the person: a DRIVER duty needs a staff member of type DRIVER, and so on → 409 `WRONG_STAFF_TYPE`.
12. A driver whose licence ends before the from date cannot be assigned → 409 `LICENCE_ENDED`.

### Routes

13. A vehicle runs at most one active route → 409 `VEHICLE_HAS_ROUTE`.
14. `PUT /routes/{id}/stops` receives the whole ordered list. The server sets `seq_no` 1, 2, 3 from the order.
15. A stop where children board cannot be removed → 409 `STOP_HAS_STUDENTS`. (No children exist before Phase 3. Build the check now through `StudentCounts`, an interface that returns 0 for now.)

### Routes and load

16. For each active route:
    - `children` = children on the route today (0 until Phase 3)
    - `seats` = seats of its vehicle
    - `load` = children ÷ seats
    - `yearlyCost` = vehicle monthly cost × `transport.months_operated`
    - `costPerChild` = yearlyCost ÷ children (null when children = 0)
    - `feeGot` = children × `transport.bus_fee_per_year` × `transport.collection_pct` ÷ 100
    - `surplus` = feeGot − yearlyCost
    - `verdict`: `NO_VEHICLE`, `NO_CHILDREN`, `OVER` (children > seats), `THIN` (load < 0.6), `OK`

    Example: Route 4, 19 children, Van 4 with 14 seats, ₹30,300 a month, 11 months, fee ₹8,800, 95% collected.
    load 1.36, over by 5, yearly cost ₹3,33,300, cost per child ₹17,542, fee got ₹1,58,840, surplus −₹1,74,460, verdict `OVER`.

17. Put this maths in one class with no Spring in it: `LoadBoardCalculator`. It is easy to test and easy to trust.

## Tasks

- [x] 2.1 `V2__vehicles_staff_routes.sql`.
- [x] 2.2 `vehicle` package: `Vehicle`, `VehicleDocument`, enums, repositories.
- [x] 2.3 `VehicleService` + `VehicleController`: create, update, turn off, documents, paper status (rules 1 to 3).
- [x] 2.4 `staff` package: `Staff`, repository, `StaffService`, `StaffController` (rules 4, 5).
- [x] 2.5 `VehicleAssignment` entity and repository with the query "rows of this vehicle covering day D".
- [x] 2.6 `AssignmentService.onDate(vehicleId, date)` → driver, attendant, helper (rule 9). Unit test this first.
- [x] 2.7 `AssignmentService.change(...)` (rules 6 to 8, 10 to 12).
- [x] 2.8 `GET /vehicles` and `GET /vehicles/{id}` now include today's driver, attendant, helper and route.
- [x] 2.9 `GET /vehicles/{id}/assignments` (history, newest first) and `GET /vehicles/attention`.
- [x] 2.10 `route` package: `Route`, `RouteStop`, repositories, `RouteService`, `RouteController` (rules 13 to 15).
- [x] 2.11 `LoadBoardCalculator` (pure Java) with `LoadBoardCalculatorTest`.
- [x] 2.12 `LoadBoardService` + `GET /routes/load-board` with totals for the whole fleet.
- [x] 2.13 `AttendantRouteService.routeFor(userId, date)`: user → staff → vehicle where they are ATTENDANT on that day → active route. Phase 4 depends on this.
- [x] 2.14 Change the user rule from Phase 1: an ATTENDANT user's `staffId` must be a real staff row of type ATTENDANT.
- [x] 2.15 Audit: every create, update and assignment change writes an `audit_log` row with a readable summary, for example "Driver changed from Jagdish to Surender, 12 to 16 Oct".
- [x] 2.16 A dev-only data loader (`@Profile("dev")`): 9 vehicles (7 small vans with 14 seats, 2 mid buses with 26 seats, ₹30,300 a month each), 9 routes, drivers and attendants. So the screens have data while you build.

## Tests that must pass

- `registrationNumberIsUniqueIgnoringCaseAndSpaces`
- `vehicleWithRouteCannotBeTurnedOff`
- `paperEndingIn21DaysIsEndingSoon` (fixed `Clock` at 7 Oct 2026)
- `temporaryDriverWinsOnlyInsideHisDates`
- `afterTemporaryPeriodThePermanentDriverIsBack`
- `permanentChangeClosesTheOldRow`
- `samePersonOnTwoVehiclesSameDayGives409`
- `driverDutyNeedsADriver`
- `driverWithEndedLicenceCannotBeAssigned`
- `stopsAreSavedInTheOrderSent`
- `vehicleCannotRunTwoActiveRoutes`
- `LoadBoardCalculatorTest`: the Route 4 example above, a route with no vehicle, a route with no children, a thin route
- `attendantRouteFollowsTheAssignmentOfTheDay`
- Permission tests: `ADMISSIONS_DESK` gets 403 on `/vehicles`; `OFFICE_ADMIN` can GET but not POST.

## Done when

- With the dev data loaded, `GET /routes/load-board` shows 9 routes and 150 seats in total.
- You change the driver of Van 4 for 12 to 16 Oct. `GET /vehicles/4` on a fixed date inside those days shows the new driver, and outside shows the old one.
- `GET /vehicles/attention` lists a paper you set to end in 10 days.
- `/check-phase 2` passes.

## Out of scope

- Students and child counts (Phase 3).
- Uploading scanned copies of papers. Only the dates are stored now.
- Fuel, maintenance, GPS.
