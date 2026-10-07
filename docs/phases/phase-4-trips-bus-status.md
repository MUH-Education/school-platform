# Phase 4 — Trips and bus status

## Goal

An attendant can tap children on their own route, also with no signal. The office sees where every bus is, and gets a warning when something is wrong.

This is the heart of the system. Take the most care here.

## Screens this makes work

- **M2 to M7** (attendant phone app)
- **1. Bus status**
- **2. One bus (Route 4)** (the "SMS to parent" column fills in Phase 5)

## Read first

- `docs/01-overview.md` → "The most important flow"
- `docs/03-data-model.md` → `boarding_event`
- `docs/06-api.md` → Trips and bus status
- `.claude/rules/security.md` → Attendant scope

## Tables

`V4__trips.sql`: `boarding_event` with the unique constraint `(student_id, service_date, event_type)`.

## Endpoints

`GET /trips/my-route`, `GET /trips/manifest`, `POST /trips/marks`, `GET /bus-status`, `GET /bus-status/routes/{routeId}`, `GET /bus-status/attention`.

## Rules

### Who may do what

1. A user with only `TRIPS_RECORD` (an attendant) may read and write **only their own route of today**. The route comes from `AttendantRouteService` (Phase 2), never from the request.
   Example: Balwan's route today is Route 4. `GET /trips/manifest?routeId=7` → 403 `NOT_YOUR_ROUTE`.
2. A user with `TRIPS_RECORD_ANY` (office) may use any route and any date.
3. An attendant may send taps for **today and yesterday only**. Yesterday is allowed because a phone may have been offline overnight.

### Saving taps (`POST /trips/marks`)

4. The request holds a list. Handle each tap alone. One bad tap does not stop the others. The answer has one result per tap, in the same order.
5. For each tap, check in this order:
   - the student exists and is ACTIVE, else `UNKNOWN_STUDENT`
   - the student is on a route on `serviceDate`, else `NOT_ON_BUS`
   - for an attendant: that route is their own, else `NOT_YOUR_ROUTE`
   - for an attendant: the date is today or yesterday, else `DATE_NOT_ALLOWED`
   - `NOT_TRAVELLING` is only allowed with `BOARDED_EVENING`, else `INVALID_OUTCOME`
6. **Same tap twice is saved once.** Find the row for (student, date, event). If none, insert. If one exists, update it.
7. **An older tap never overwrites a newer one.** If the stored `occurred_at` is later than the incoming one, ignore the incoming tap and answer `ok: true`.
   Example: at 7:42 the attendant taps DONE offline. At 7:43 online he corrects it to ABSENT. At 7:55 the phone finally sends the old 7:42 tap. The row stays ABSENT.
8. `CLEARED` deletes the row (undo).
9. `occurredAt` comes from the phone. If it is more than 5 minutes in the future, use the server time. Phones have wrong clocks.
10. When a tap becomes `DONE` for the first time, call `BoardingNotifier.onDone(...)`. In this phase it is an empty class. Phase 5 puts the SMS logic behind it. It must run in the same transaction as the save.

### The manifest (`GET /trips/manifest`)

11. Returns the stops in morning order. Under each stop, the children who are on the route **on that date**, with their four events so far.
12. It does **not** return parents' phone numbers (decision C10).
13. A child whose bus starts tomorrow is not in today's manifest.

### Bus status

Bus status is **calculated** from taps. Nothing is stored. Put the maths in one pure class, `BusStatusCalculator`, with no Spring in it.

14. **Stops (morning).** Find the last stop, in order, that has at least one morning tap. That stop and all before it are `DONE`. The stop after it is `NEXT`. The rest are `LATER`. `tappedAt` is the first tap time at that stop.
15. **Route state (morning)**, checked top to bottom at time T:
    | State | When |
    |---|---|
    | `REACHED_SCHOOL` | at least one `REACHED_SCHOOL` tap. Time = the first one. |
    | `NOT_STARTED` | no taps, and T is before the first stop time + `late_after_minutes` |
    | `NO_TAPS` | no taps, and T is later than that. `lateMinutes` = T − first stop time. |
    | `LATE` | has taps, and `lateMinutes` ≥ `late_after_minutes` |
    | `ON_THE_WAY` | has taps, not late |

16. **`lateMinutes`** when there are taps = the larger of:
    - last done stop: `tappedAt` − its due time
    - next stop: T − its due time (only if positive)

    Example at 7:48 with `late_after_minutes` = 10:
    - Route 3: no taps, first stop due 7:15 → `NO_TAPS`, 33 minutes.
    - Route 5: Samain due 7:24, tapped 7:40 → 16 minutes → `LATE`.
    - Route 9: no taps, first stop due 7:50 → `NOT_STARTED`.
    - Route 2: has a `REACHED_SCHOOL` tap at 7:46 → `REACHED_SCHOOL`.

17. **Evening.** The same idea with `BOARDED_EVENING` and `REACHED_HOME`, and the stops in reverse order. States: `NOT_STARTED`, `BOARDING`, `ON_THE_WAY`, `DONE`.
18. `GET /bus-status?phase=MORNING|EVENING`. Without `phase`: MORNING before 12:00, EVENING after.
19. Counts per route: `total` (children on the route today), `boarded`, `absent`.

### Attention list (`GET /bus-status/attention`)

20. `NO_TAPS` and `LATE` routes (from rule 15).
21. **Missing in the evening:** the route has at least one `BOARDED_EVENING` tap, and a child who came in the morning (morning `DONE` or `REACHED_SCHOOL` `DONE`) has **no** evening tap at all. This is the most serious warning in the system.
    Example: Neha boarded in the morning. Evening boarding started at 2:40. At 2:50 Neha has no evening tap → she is on the list with her class and stop.
22. A child marked `NOT_TRAVELLING` or `ABSENT` in the evening is **not** missing. Someone answered for them.

## Tasks

- [x] 4.1 `V4__trips.sql`.
- [x] 4.2 `trip` package: `BoardingEvent` entity, enums `EventType`, `Outcome`, repository.
- [x] 4.3 `TripAccess`: one class that answers "may this user touch this route on this date?" (rules 1 to 3). Every trip endpoint goes through it. Unit test it.
- [x] 4.4 `BoardingNotifier` interface + `NoOpBoardingNotifier`.
- [ ] 4.5 `MarkService.apply(user, marks)` (rules 4 to 10). Write the tests first.
- [ ] 4.6 `POST /trips/marks`.
- [ ] 4.7 `ManifestService` + `GET /trips/manifest` (rules 11 to 13).
- [ ] 4.8 `GET /trips/my-route`: the attendant's route and the count for each of the four jobs.
- [ ] 4.9 `BusStatusCalculator` (pure Java) with tests for every state, using the examples in rule 16.
- [ ] 4.10 `BusStatusService` + `GET /bus-status` and `GET /bus-status/routes/{routeId}`.
- [ ] 4.11 `AttentionService` + `GET /bus-status/attention` (rules 20 to 22).
- [ ] 4.12 Extend the dev data loader: taps for a morning at 7:48, so Bus status looks like the design.
- [ ] 4.13 A test with two threads sending the same tap at the same time. One row must exist at the end.

## Tests that must pass

Access:
- `attendantCannotOpenAnotherRoute`
- `attendantCannotTapChildOfAnotherRoute` (and nothing is saved)
- `attendantWithNoVehicleTodayGetsEmptyRoute`
- `replacementAttendantSeesTheRouteOnlyOnHisDays`
- `attendantCannotWriteThreeDaysAgo`
- `officeCanCorrectAnyRoute`

Taps:
- `sameTapSentTwiceIsSavedOnce`
- `sameTapFromTwoThreadsIsSavedOnce`
- `olderTapDoesNotOverwriteNewerOne`
- `clearedRemovesTheRow`
- `oneBadTapDoesNotStopTheOthers`
- `notTravellingIsOnlyForEvening`
- `futureTimeFromPhoneIsReplacedByServerTime`
- `childWhoseBusStartsTomorrowIsNotInTodaysManifest`
- `notifierIsCalledOnceWhenTapBecomesDone` and `...notCalledForAbsent`

Bus status (pure tests, fixed time 7:48):
- `noTapsBeforeFirstStopIsNotStarted`
- `noTaps33MinutesAfterFirstStopIsNoTaps`
- `stopTapped16MinutesLateIsLate`
- `reachedSchoolWinsOverEverything`
- `stopsAreDoneNextLater`

Attention:
- `childWhoCameInMorningWithNoEveningTapIsMissing`
- `childMarkedNotTravellingIsNotMissing`
- `nobodyIsMissingBeforeEveningBoardingStarts`

## Done when

- With two attendant logins on two routes, each can tap only their own children.
- You send the same list of taps three times with `curl`. The table has one row per child and event.
- With the dev data, `GET /bus-status` at the fixed time shows 9 routes with the states from the design.
- `/check-phase 4` passes.

Try a tap by hand (use an attendant's token):

```bash
curl -X POST localhost:8080/api/v1/trips/marks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"marks":[{"studentId":118,"eventType":"BOARDED_MORNING","outcome":"DONE","serviceDate":"2026-10-07","occurredAt":"2026-10-07T07:42:10+05:30"}]}'
```

## Out of scope

- Sending SMS (Phase 5). Only the empty `BoardingNotifier` exists.
- GPS, maps, distance, speed.
- Pushing live updates to the browser. The web app asks again every 30 seconds.
