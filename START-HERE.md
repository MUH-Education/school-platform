# Start here

This folder is the full plan for the school backend. There is no Java code in it yet. It tells you (and Claude Code) what to build, in what order, and how to know each part is finished.

## What is decided

| Topic | Decision |
|---|---|
| Stack | Spring Boot 4, Java 25, Gradle, PostgreSQL |
| Users | Registered with **phone number and role only** |
| Login | Phone number → OTP on WhatsApp (SMS if WhatsApp fails) → one JWT for 30 days |
| Parents | No app. SMS only. |
| Shape | One Spring Boot app, one database, one package per feature |

Example of a login: the clerk types her phone number, gets "Your school login code is 482913" on WhatsApp, types it, and is logged in for 30 days. No password exists.

## What is in this folder

```
school-admin-backend/
├── START-HERE.md            this file
├── CLAUDE.md                rules Claude Code reads every time it opens the project
├── .claude/
│   ├── rules/               security, database and testing rules
│   └── skills/              two commands: /next-task and /check-phase
└── docs/
    ├── 01-overview.md           what we build; which screen needs which part
    ├── 02-architecture.md       packages, layers, error format, settings
    ├── 03-data-model.md         all 26 tables, with example rows
    ├── 04-login-otp-jwt.md      the login flow, step by step
    ├── 05-roles-permissions.md  5 roles, 19 permissions, who can do what
    ├── 06-api.md                every URL
    ├── 07-messaging.md          parent SMS and login OTP
    ├── 08-decisions.md          decisions made, and 10 questions for you
    └── phases/
        ├── README.md            the phase list and status
        └── phase-0 … phase-9    one file per phase, with tick boxes
```

## The 10 phases

| Phase | You build | Screens that then work |
|---|---|---|
| 0 | Empty project that starts and tests | — |
| 1 | Login with OTP, users, roles | Login, Users and roles |
| 2 | Vehicles, drivers, attendants, routes | Vehicles and staff, One vehicle, Routes and load |
| 3 | Students, admission, edit later | New admission, Students, One student |
| 4 | Attendant taps, bus status | Attendant phone app, Bus status, One bus |
| 5 | Parent SMS, real OTP | SMS column, Messages |
| 6 | Enquiries | Enquiry list, Add an enquiry |
| 7 | Fees | Fees part of admission and student page |
| 8 | Analytics | Analytics |
| 9 | Go live | — |

**Phases 0 to 5 are the transport system.** Do these first. Phases 6, 7 and 8 can follow.

## How to begin (about one hour)

1. Install JDK 25 and Docker Desktop on your laptop.
2. Open `docs/phases/phase-0-setup.md`. It lists the exact choices for start.spring.io. Generate the project.
3. Copy `CLAUDE.md`, `.claude/` and `docs/` from this folder into the new project folder.
4. Open the project in Claude Code and type `/next-task`.

Claude then builds one task, runs the tests, ticks the box in the phase file, and tells you in simple English what it did. Type `/next-task` again for the next one. When a phase has all boxes ticked, type `/check-phase`.

You can also follow the phase files by hand. Each task is small and in build order.

## Read these three first

1. `docs/04-login-otp-jwt.md` — because you asked for this login. Check it matches what you want.
2. `docs/05-roles-permissions.md` — check the table "Which role has which permission".
3. `docs/08-decisions.md` — part B lists 21 choices I made for you. Part C lists 10 questions only you can answer.

## Start one thing today that is not code

Real SMS in India needs DLT registration, and WhatsApp OTP needs an approved template from a provider. Both take days. Choose the SMS and WhatsApp company now (question C1), so it is ready when you reach Phase 5. Until then the app prints codes and messages on your laptop screen instead of sending them.
