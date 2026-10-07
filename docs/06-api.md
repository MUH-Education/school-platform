# 6. API list

All URLs start with `/api/v1`. All requests and answers are JSON, except photo upload and CSV files.

Every URL needs a token, except the two OTP URLs. The "Permission" column is what `@PreAuthorize` checks.

This file is the agreement between the backend and the React app. If you change a URL or a field, change this file in the same commit.

## Login — Phase 1

| Method and URL | Permission | What it does |
|---|---|---|
| `POST /auth/otp/request` | open | Send a login code to a phone |
| `POST /auth/otp/verify` | open | Check the code, return the token |
| `GET /auth/me` | any login | Who am I, my role, my permissions, my route |
| `POST /auth/logout` | any login | Kill all my tokens |

Details: `docs/04-login-otp-jwt.md`.

## Users and roles — Phase 1

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /users` | `USERS_MANAGE` | All users |
| `POST /users` | `USERS_MANAGE` | Add a user: `{ "phone", "role", "name"?, "staffId"? }` |
| `PUT /users/{id}` | `USERS_MANAGE` | Change phone, name, role, `active` |
| `GET /roles` | `USERS_MANAGE` | The role and permission table |
| `GET /settings` | any login | School-wide numbers |
| `PUT /settings` | `SETTINGS_EDIT` | Change them |

## Vehicles and staff — Phase 2

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /vehicles` | `VEHICLES_VIEW` | All vehicles with today's driver, attendant, route and papers status |
| `POST /vehicles` | `VEHICLES_EDIT` | Add a vehicle |
| `GET /vehicles/{id}` | `VEHICLES_VIEW` | One vehicle: details, papers, people today |
| `PUT /vehicles/{id}` | `VEHICLES_EDIT` | Change details |
| `DELETE /vehicles/{id}` | `VEHICLES_EDIT` | Turn off. 409 `VEHICLE_IN_USE` if it runs a route. |
| `PUT /vehicles/{id}/documents` | `VEHICLES_EDIT` | Save the four paper dates |
| `GET /vehicles/{id}/assignments` | `VEHICLES_VIEW` | Who worked on it, newest first |
| `POST /vehicles/{id}/assignments` | `VEHICLES_EDIT` | Change the driver, attendant or helper |
| `GET /vehicles/attention` | `VEHICLES_VIEW` | Papers and licences ended or ending in 30 days |
| `GET /staff` | `VEHICLES_VIEW` | All drivers, attendants, helpers, with where they work today |
| `POST /staff` | `VEHICLES_EDIT` | Add a person |
| `PUT /staff/{id}` | `VEHICLES_EDIT` | Change a person |
| `DELETE /staff/{id}` | `VEHICLES_EDIT` | Turn off. 409 if still on a vehicle. |

`GET /vehicles` and `GET /vehicles/{id}` give `driver`, `attendant` and `helper` (each a person or null). `GET /staff` gives `worksOn` (the vehicle and duty today, or null). Both answer for today. `GET /vehicles` and `GET /vehicles/{id}` also take an optional `?date=2026-10-14` and then show the people of that day. A person with `"temporary": true` is a replacement.

`PUT /vehicles/{id}` and `PUT /staff/{id}` take the whole object, with `active`. `active: false` is the same as `DELETE`, `active: true` turns it on again. `PUT /vehicles/{id}/documents` takes the four dates: `{ "fitness": "2027-01-10", "insurance": "2026-10-28", "permit": null, "puc": null }`. A date that is null or left out removes that paper.

`GET /vehicles/attention` is a list, the most urgent first. A paper: `{ "kind": "PAPER", "vehicleId": 4, "vehicleName": "Van 4", "docType": "INSURANCE", "validTill": "2026-10-17", "status": "ENDING_SOON", "daysLeft": 10 }`. A licence: `{ "kind": "LICENCE", "staffId": 21, "staffName": "Jagdish", "validTill": "2026-10-12", "status": "ENDING_SOON", "daysLeft": 5 }`. Only turned-on vehicles and drivers. A paper with no date is not listed. `daysLeft` is below 0 when ended.

`GET /vehicles/{id}/assignments` is a list, newest first: `{ "id", "vehicleId", "staffId", "staffName", "duty", "fromDate", "toDate", "temporary", "reason", "createdBy", "createdAt" }`.

Change a driver:

```json
POST /api/v1/vehicles/4/assignments
{
  "duty": "DRIVER",
  "staffId": 21,
  "fromDate": "2026-10-12",
  "toDate": "2026-10-16",
  "temporary": true,
  "reason": "ON_LEAVE"
}
```

A permanent change has no `toDate`: `{ "duty": "DRIVER", "staffId": 22, "fromDate": "2026-11-01" }`. The old permanent row ends on 31 Oct.

Errors (all 409): `STAFF_BUSY` if that person is on a vehicle on those days, the message names it: "Rajpal drives Van 1 on these days." `WRONG_STAFF_TYPE` (a DRIVER duty needs a DRIVER), `LICENCE_ENDED`, `STAFF_INACTIVE`, `VEHICLE_INACTIVE`, `FROM_DATE_TOO_EARLY` (a permanent change must start after the current person started), `TEMPORARY_OVERLAP` (two replacements for the same duty on the same day).

## Routes — Phase 2

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /routes` | `ROUTES_VIEW` | All routes with vehicle and stops |
| `POST /routes` | `ROUTES_EDIT` | Add a route |
| `GET /routes/{id}` | `ROUTES_VIEW` | One route with stops and child count per stop |
| `PUT /routes/{id}` | `ROUTES_EDIT` | Change name or vehicle |
| `DELETE /routes/{id}` | `ROUTES_EDIT` | Turn off. 409 `ROUTE_HAS_STUDENTS` if children are on it. |
| `PUT /routes/{id}/stops` | `ROUTES_EDIT` | Save the full ordered list of stops |
| `GET /routes/load-board` | `ROUTES_VIEW` | The Routes and load screen: every route with children, seats, load, cost |

`POST /routes` takes `{ "name": "Route 4", "vehicleId": 4 }` (`vehicleId` is optional). `PUT /routes/{id}` takes the whole route: `{ "name": "Route 4", "vehicleId": 4, "active": true }`. `active: false` is the same as `DELETE`, `active: true` turns it on again. Other 409 codes: `VEHICLE_HAS_ROUTE`, `VEHICLE_INACTIVE`, `ROUTE_NAME_ALREADY_USED`. A vehicle on `GET /vehicles` has a `route` (`{ "id": 4, "name": "Route 4" }` or null).

`PUT /routes/4/stops` sends the whole list in order. A stop with an `id` is kept. A stop without an `id` is new. A stop missing from the list is removed (409 `STOP_HAS_STUDENTS` if children board there).

```json
[
  { "id": 11, "name": "Sadhanwas", "morningTime": "07:25" },
  { "id": 12, "name": "Jakhal", "morningTime": "07:40" },
  { "name": "New stop", "morningTime": "07:48" }
]
```

One row of `GET /routes/load-board`:

```json
{
  "routeId": 4, "name": "Route 4", "vehicle": "Van 4", "vehicleType": "SMALL_VAN",
  "seats": 14, "children": 19, "load": 1.36, "overBy": 5, "spare": 0,
  "yearlyCost": 333300.00, "costPerChild": 17542.11,
  "feeGot": 158840.00, "surplus": -174460.00,
  "verdict": "OVER"
}
```

## Students — Phase 3

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /students` | `STUDENTS_VIEW` | Paged list. Filters: `q`, `className`, `village`, `routeId`, `bus=YES|NO` |
| `GET /students/{id}` | `STUDENTS_VIEW` | Full profile: details, parents, bus now, photo flag |
| `PUT /students/{id}` | `STUDENTS_EDIT` | Change details (name, class, section, village, ...) |
| `POST /students/{id}/guardians` | `STUDENTS_EDIT` | Add a phone number |
| `PUT /students/{id}/guardians/{guardianId}` | `STUDENTS_EDIT` | Change name, relation, SMS on or off |
| `DELETE /students/{id}/guardians/{guardianId}` | `STUDENTS_EDIT` | Remove the link. 409 if it is the last phone. |
| `GET /students/{id}/transport` | `STUDENTS_VIEW` | Bus history |
| `PUT /students/{id}/transport` | `STUDENTS_EDIT` | Start the bus, change route or stop, or stop the bus |
| `POST /students/{id}/photo` | `STUDENTS_EDIT` | Upload a photo (multipart, JPEG or PNG, max 2 MB) |
| `GET /students/{id}/photo` | `STUDENTS_VIEW` | The photo bytes |
| `DELETE /students/{id}/photo` | `STUDENTS_EDIT` | Remove the photo |
| `GET /students/{id}/history` | `STUDENTS_VIEW` | Change history from the audit log |
| `POST /students/import` | `STUDENTS_EDIT` | Upload a CSV of existing students |
| `POST /admissions` | `ADMISSIONS_CREATE` | New admission: student + parents + bus (+ fee plan from Phase 7) in one go |

Start the bus later, or change route:

```json
PUT /api/v1/students/118/transport
{ "usesBus": true, "routeId": 9, "stopId": 44, "fromDate": "2026-11-02", "busFee": 4000 }
```

Stop the bus: `{ "usesBus": false, "fromDate": "2026-12-01" }`.

The answer includes a warning when the route is over its seats. It is a warning, not an error:

```json
{ "saved": true, "warning": { "code": "ROUTE_FULL", "message": "Route 9 has 45 children on 26 seats." } }
```

## Trips and bus status — Phase 4

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /trips/my-route` | `TRIPS_RECORD` | The attendant's route today and the progress of the four jobs |
| `GET /trips/manifest?routeId=&date=` | `TRIPS_RECORD` or `TRIPS_RECORD_ANY` | Stops in order, children at each stop, taps so far |
| `POST /trips/marks` | `TRIPS_RECORD` or `TRIPS_RECORD_ANY` | Save one or many taps |
| `GET /bus-status?date=` | `BUS_STATUS_VIEW` | Every route: where the bus is, counts, state |
| `GET /bus-status/routes/{routeId}?date=` | `BUS_STATUS_VIEW` | One route: every child with the four events |
| `GET /bus-status/attention?date=` | `BUS_STATUS_VIEW` | Buses with no taps, late buses, children missing in the evening |

With `TRIPS_RECORD` only, the server also checks that the route is the attendant's own route today.

Send taps (one or many, because the phone may have been offline):

```json
POST /api/v1/trips/marks
{
  "marks": [
    { "studentId": 118, "eventType": "BOARDED_MORNING", "outcome": "DONE",
      "serviceDate": "2026-10-07", "occurredAt": "2026-10-07T07:42:10+05:30" },
    { "studentId": 131, "eventType": "BOARDED_MORNING", "outcome": "ABSENT",
      "serviceDate": "2026-10-07", "occurredAt": "2026-10-07T07:43:02+05:30" }
  ]
}
```

`outcome` is `DONE`, `ABSENT`, `NOT_TRAVELLING` (evening only) or `CLEARED` (undo a wrong tap).

The answer has one result per tap, in the same order. One bad tap does not stop the others:

```json
{ "results": [ { "studentId": 118, "eventType": "BOARDED_MORNING", "ok": true },
               { "studentId": 131, "eventType": "BOARDED_MORNING", "ok": false, "error": "NOT_YOUR_ROUTE" } ] }
```

One route in `GET /bus-status`:

```json
{
  "routeId": 4, "name": "Route 4", "vehicle": "Van 4", "attendant": "Balwan",
  "phase": "MORNING", "state": "ON_THE_WAY", "lateMinutes": 0,
  "boarded": 11, "absent": 1, "total": 19,
  "stops": [
    { "name": "Sadhanwas", "due": "07:25", "tappedAt": "07:26", "state": "DONE" },
    { "name": "Jakhal", "due": "07:40", "tappedAt": "07:42", "state": "DONE" },
    { "name": "Kanheri", "due": "07:55", "tappedAt": null, "state": "NEXT" },
    { "name": "Tohana town", "due": "08:02", "tappedAt": null, "state": "LATER" }
  ]
}
```

`state` is one of `NOT_STARTED`, `ON_THE_WAY`, `LATE`, `NO_TAPS`, `REACHED_SCHOOL`, `DONE`. The exact rules are in `docs/phases/phase-4-trips-bus-status.md`.

## Messages — Phase 5

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /messages` | `MESSAGES_VIEW` | Paged SMS log. Filters: `date`, `status`, `studentId`, `phone` |
| `GET /messages/summary?date=` | `MESSAGES_VIEW` | Counts for one day: queued, sent, failed |
| `GET /message-templates` | `MESSAGES_VIEW` | The templates |
| `PUT /message-templates/{code}` | `SETTINGS_EDIT` | Change text or provider template id |

## Enquiries — Phase 6

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /enquiries` | `ENQUIRIES_VIEW` | Paged list. Filters: `status`, `village`, `source`, `overdue=true`, `q` |
| `GET /enquiries/summary` | `ENQUIRIES_VIEW` | Count per stage, overdue count, admitted % |
| `POST /enquiries` | `ENQUIRIES_EDIT` | Add an enquiry |
| `GET /enquiries/{id}` | `ENQUIRIES_VIEW` | One enquiry with its follow-ups |
| `PUT /enquiries/{id}` | `ENQUIRIES_EDIT` | Change details |
| `POST /enquiries/{id}/follow-ups` | `ENQUIRIES_EDIT` | Add a call or visit note, set the next date |
| `POST /enquiries/{id}/status` | `ENQUIRIES_EDIT` | Move to another stage. LOST needs a reason. |
| `GET /enquiries/{id}/prefill` | `ADMISSIONS_CREATE` | The fields the admission form can copy from this enquiry |

## Fees — Phase 7

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /sessions` | any login | Academic sessions |
| `POST /sessions` | `SETTINGS_EDIT` | Add a session |
| `GET /sessions/{id}/class-fees` | `FEES_VIEW` | School fee per class |
| `PUT /sessions/{id}/class-fees` | `SETTINGS_EDIT` | Save school fee per class |
| `GET /students/{id}/fees` | `FEES_VIEW` | Plan, dues, payments, pending now, still to pay this year, status |
| `PUT /students/{id}/fee-plan` | `FEES_EDIT` | Create or change the plan for the current session |
| `POST /students/{id}/payments` | `FEES_EDIT` | Record money received. Returns the receipt number. |
| `POST /students/{id}/payment-corrections` | `FEES_CORRECT` | Fix a wrong payment with a negative row and a note. Owner only. |
| `GET /payments?from=&to=` | `FEES_VIEW` | Paged list of payments |

## Analytics — Phase 8

All take the same filters: `sessionId`, `className`, `village`, `routeId`, `occupation`, `feeStatus`.

| Method and URL | Permission | What it does |
|---|---|---|
| `GET /analytics/summary` | `ANALYTICS_VIEW` | Students, on the bus, % school fee collected, % bus fee collected, students with fee pending |
| `GET /analytics/fee-collection-by-month` | `ANALYTICS_VIEW` | Per month: % of school fee and % of bus fee collected |
| `GET /analytics/payment-by-occupation` | `ANALYTICS_VIEW` | Per occupation: on time, delayed, defaulted |
| `GET /analytics/students-by-class` | `ANALYTICS_VIEW` | Count per class |
| `GET /analytics/students-by-village` | `ANALYTICS_VIEW` | Count per village, biggest first |
| `GET /analytics/students` | `ANALYTICS_VIEW` | Paged list with fee status and pending amount |
| `GET /analytics/students.csv` | `ANALYTICS_VIEW` | The same list as a file that opens in Excel |

## Health

`GET /actuator/health` is open. It answers `{"status":"UP"}`. The uptime checker calls it.
