# 1. Overview

## What we are building

One backend for the school. It serves two apps and sends SMS.

| Who | App | What they do | Example |
|---|---|---|---|
| Owner, office staff, transport in-charge, admissions desk | Admin web app (React, on a laptop) | Run the school's records | "Add Van 10 and make Surender its driver." |
| Bus attendant | Phone app (same React app, Hindi) | Tap each child at each stop | "आर्यन · चढ़ गए" at Jakhal, 7:42 |
| Parent | **No app** | Gets SMS | "आर्यन सुबह की बस में चढ़ गया — 7:42" |

There is no parent app and no parent login. This is decided.

## The modules

Each module is one Java package. The "Phase" column says when it is built.

| Module | What it holds | Phase |
|---|---|---|
| `auth` | OTP login, JWT | 1 |
| `user` | People who can log in, their role | 1 |
| `audit` | "Who changed what, and when" | 1 |
| `vehicle` | Vehicles and their papers (fitness, insurance, permit, pollution) | 2 |
| `staff` | Drivers, attendants, helpers. Who is on which vehicle, with dates. | 2 |
| `route` | Routes, stops, and the Routes and load numbers | 2 |
| `student` | Students, parents' phone numbers, photo, bus enrolment | 3 |
| `trip` | Attendant taps, bus status, alerts | 4 |
| `messaging` | SMS and WhatsApp queue, templates, providers | 5 |
| `enquiry` | Admission enquiries and follow-ups | 6 |
| `fee` | Fee plan, dues, payments | 7 |
| `analytics` | Read-only numbers for graphs | 8 |

## Which screen needs which module

The screens are in the design canvas "School Admin Web App — Screens".

| Screen | Needs | Phase |
|---|---|---|
| M1. Login (phone app) and web login | `auth` | 1 |
| 8. Users and roles | `user` | 1 |
| 11. Vehicles and staff | `vehicle`, `staff` | 2 |
| 12. One vehicle | `vehicle`, `staff` | 2 |
| 3. Routes and load | `route`, `vehicle`, `student` (counts) | 2, numbers complete after 3 |
| 9. Students | `student` | 3 |
| 10. One student (edit) | `student`, `audit`; fees card after 7 | 3 |
| 6. New admission | `student`; fees part after 7 | 3 and 7 |
| M2 to M7. Attendant app | `trip` | 4 |
| 1. Bus status | `trip` | 4 |
| 2. One bus | `trip`, `messaging` (SMS column) | 4 and 5 |
| Messages (menu item, not drawn yet) | `messaging` | 5 |
| 4. Enquiry list, 5. Add an enquiry | `enquiry` | 6 |
| 7. Analytics | `analytics`, `fee`, `student` | 8 |

## The most important flow

This one flow is why the system exists. Every design choice protects it.

1. 7:42. Attendant Balwan taps "चढ़ गए" for Aryan at Jakhal. The phone has no signal.
2. The phone saves the tap with the time 7:42.
3. 7:55. Signal returns. The phone sends the tap to `POST /api/v1/trips/marks`.
4. The server checks: is Balwan the attendant of Aryan's route today? Yes.
5. The server saves one `boarding_event` row. A second copy of the same tap changes nothing.
6. The server looks at Aryan's class (3). Class 3 gets this SMS. It writes one row per parent phone into `message_outbox`.
7. A background job sends the SMS. Aryan's mother reads: "आर्यन सुबह की बस में चढ़ गया — 7:42".
8. The office opens Bus status and sees Route 4 at Jakhal.

## What is out

- Parent app, parent login.
- Live GPS map. Bus position comes from taps.
- Online fee payment. The office records a payment after it happens.
- Marks, homework, report cards.
- The React frontend. This plan is the backend only.

## Scale

290 students today, target 648. About 1,300 parent phone numbers at full size. 9 vehicles, about 20 staff, about 15 logins. This is small data. One server and one database are enough.
