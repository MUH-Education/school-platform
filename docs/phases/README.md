# Phases

The backend is built in 10 phases. Each phase ends with something you can run and show.

## Status

Change the Status column as you go: **Not started** → **In progress** → **Done**. Only one phase is In progress at a time.

| Phase | Name | What works at the end | Size | Status |
|---|---|---|---|---|
| 0 | [Project setup](phase-0-setup.md) | Empty app starts, connects to PostgreSQL, tests run | S | Done |
| 1 | [Login, users, roles](phase-1-login-users.md) | Log in with phone and OTP. Owner adds users. | M | Done |
| 2 | [Vehicles, staff, routes](phase-2-vehicles-staff-routes.md) | Add vehicles, drivers, attendants. Change who is assigned. Routes and stops. | M | Done |
| 3 | [Students and admission](phase-3-students-admission.md) | Admit a student, edit later, add phone numbers, photo, start or change bus | L | Done |
| 4 | [Trips and bus status](phase-4-trips-bus-status.md) | Attendant taps children. Office sees every bus. | L | Done |
| 5 | [SMS and WhatsApp](phase-5-sms-whatsapp.md) | Parents get Hindi SMS. Real OTP on WhatsApp or SMS. | M | Done |
| 6 | [Enquiries](phase-6-enquiries.md) | Enquiry list with stages and follow-ups | S | Done |
| 7 | [Fees](phase-7-fees.md) | Fee plan at admission, payments, pending amount, status | L | In progress |
| 8 | [Analytics](phase-8-analytics.md) | Filters, graph numbers, download | M | Not started |
| 9 | [Go live](phase-9-go-live.md) | Running on a server in India with backups | M | Not started |

Size: **S** is about one week, **M** about one and a half weeks, **L** about two weeks. This assumes about 10 to 12 hours a week with Claude Code doing most of the typing. It is a guess, not a promise.

## The order matters

```
0 Setup ─► 1 Login ─► 2 Vehicles/routes ─► 3 Students ─► 4 Trips ─► 5 SMS ─► 9 Go live
                                              │
                                              ├─► 6 Enquiries
                                              └─► 7 Fees ─► 8 Analytics
```

- **Phases 0 to 5 are the transport system.** After Phase 5 the buses and SMS work. This is the part to finish before admission season.
- Phase 6 (enquiries) needs only Phase 1. You can do it any time after Phase 1, for example in a quiet week.
- Phase 7 (fees) needs Phase 3. Phase 8 (analytics) needs Phase 7.
- Phase 9 can be done in two parts: a first go-live after Phase 5, and again after Phase 8.

Example plan: if you start on 8 October and keep the pace above, Phases 0 to 5 take about 9 to 10 weeks, so the transport system is ready around the middle of December. Phases 6 to 8 then follow in December and January.

## What every phase file contains

| Section | Meaning |
|---|---|
| Goal | One sentence |
| Screens this makes work | Which designs can now be connected |
| Read first | Docs to read before coding |
| Tables | The Flyway file to write |
| Endpoints | URLs to build |
| Rules | Business rules, each with an example |
| Tasks | Tick boxes, in build order |
| Tests that must pass | Write these first |
| Done when | How you know the phase is finished |
| Out of scope | Things not to build now |

## How to work with Claude Code

1. Open this folder in Claude Code.
2. Type `/next-task`. Claude builds the next unticked task of the current phase, runs the tests, ticks the box, and explains what it did.
3. Try what it built. Then type `/next-task` again.
4. When all tasks of a phase are ticked, type `/check-phase`. Claude goes through "Done when" and tells you pass or fail for each line.
5. Commit to Git after every task. Then a bad change is easy to undo.

You can also work by hand. The phase files are written so a person can follow them without Claude.
