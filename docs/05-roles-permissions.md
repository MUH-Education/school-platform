# 5. Roles and permissions

## The idea

- A **user** has exactly one **role**.
- A role is a fixed bundle of **permissions**.
- A **permission** is one small thing a person may do, for example `STUDENTS_EDIT`.
- Controllers check permissions, never roles.

Real-life picture: the role is the job title on the ID card ("Admissions desk"). The permissions are the keys on the key ring. A door checks for the key, not for the job title.

Why check permissions and not roles? Later you may want a new role "Accountant". You add one line to the role map. No controller changes.

Roles and their permissions are **fixed in Java code** (two enums). They are not editable in the app. The Users and roles screen only shows the table.

## The five roles

| Role | Who | Uses |
|---|---|---|
| `OWNER` | You | Everything |
| `OFFICE_ADMIN` | Office clerk | Web app |
| `TRANSPORT_INCHARGE` | Runs the buses | Web app |
| `ADMISSIONS_DESK` | Talks to new parents | Web app |
| `ATTENDANT` | On the bus | Phone app only |

## The permissions

| Permission | Allows | Example URL |
|---|---|---|
| `BUS_STATUS_VIEW` | See where every bus is today | `GET /api/v1/bus-status` |
| `TRIPS_RECORD` | Tap children on **own** route | `POST /api/v1/trips/marks` |
| `TRIPS_RECORD_ANY` | Tap or correct on any route | same URL, any route |
| `ROUTES_VIEW` | See routes, stops, load and cost | `GET /api/v1/routes` |
| `ROUTES_EDIT` | Add or change routes and stops | `PUT /api/v1/routes/{id}/stops` |
| `VEHICLES_VIEW` | See vehicles, papers, staff | `GET /api/v1/vehicles` |
| `VEHICLES_EDIT` | Add or change vehicles, staff, assignments | `POST /api/v1/vehicles/{id}/assignments` |
| `STUDENTS_VIEW` | See students and parents' numbers | `GET /api/v1/students` |
| `STUDENTS_EDIT` | Change a student, photo, parents, bus | `PUT /api/v1/students/{id}` |
| `ADMISSIONS_CREATE` | Create a new admission | `POST /api/v1/admissions` |
| `MESSAGES_VIEW` | See the SMS log | `GET /api/v1/messages` |
| `ENQUIRIES_VIEW` | See enquiries | `GET /api/v1/enquiries` |
| `ENQUIRIES_EDIT` | Add or change enquiries | `POST /api/v1/enquiries` |
| `FEES_VIEW` | See fee plans, dues, payments | `GET /api/v1/students/{id}/fees` |
| `FEES_EDIT` | Record a payment, change a plan | `POST /api/v1/students/{id}/payments` |
| `FEES_CORRECT` | Add a correction to a wrong payment | `POST /api/v1/students/{id}/payment-corrections` |
| `ANALYTICS_VIEW` | See graphs and the filtered list | `GET /api/v1/analytics/summary` |
| `USERS_MANAGE` | Add users, change roles, turn off | `POST /api/v1/users` |
| `SETTINGS_EDIT` | Change school-wide numbers | `PUT /api/v1/settings` |

## Which role has which permission

This matches the "Users and roles" screen in the design.

| Permission | Owner | Office admin | Transport in-charge | Admissions desk | Attendant |
|---|---|---|---|---|---|
| `BUS_STATUS_VIEW` | yes | yes | yes | | |
| `TRIPS_RECORD` | yes | | yes | | yes |
| `TRIPS_RECORD_ANY` | yes | yes | yes | | |
| `ROUTES_VIEW` | yes | yes | yes | | |
| `ROUTES_EDIT` | yes | | yes | | |
| `VEHICLES_VIEW` | yes | yes | yes | | |
| `VEHICLES_EDIT` | yes | | yes | | |
| `STAFF_SALARY_VIEW` | yes | | | | |
| `STAFF_SALARY_EDIT` | yes | | | | |
| `STUDENTS_VIEW` | yes | yes | yes | yes | |
| `STUDENTS_EDIT` | yes | yes | | | |
| `ADMISSIONS_CREATE` | yes | yes | | yes | |
| `MESSAGES_VIEW` | yes | yes | yes | | |
| `ENQUIRIES_VIEW` | yes | yes | | yes | |
| `ENQUIRIES_EDIT` | yes | yes | | yes | |
| `FEES_VIEW` | yes | yes | | yes | |
| `FEES_EDIT` | yes | yes | | yes | |
| `FEES_CORRECT` | yes | | | | |
| `ANALYTICS_VIEW` | yes | yes | | yes | |
| `USERS_MANAGE` | yes | | | | |
| `SETTINGS_EDIT` | yes | | | | |

Examples:

- Priya (Admissions desk) calls `GET /api/v1/vehicles`. She has no `VEHICLES_VIEW`. Answer: 403.
- Jaswant (Transport in-charge) calls `GET /api/v1/enquiries`. No `ENQUIRIES_VIEW`. Answer: 403.
- Balwan (Attendant) calls `GET /api/v1/students`. No `STUDENTS_VIEW`. Answer: 403. He sees children only through his own route's manifest.
- Jaswant (Transport in-charge) calls `GET /api/v1/staff/31/salary`. He has `VEHICLES_VIEW`, so he can see the staff list, but he has no `STAFF_SALARY_VIEW`. Answer: 403. Only the owner sees a salary.

## The attendant has one more check

`TRIPS_RECORD` alone is not enough. The server also checks **which route**.

1. Find the attendant's route today (see the third query in `docs/03-data-model.md`).
2. The request must be about that route, or about a child who is on that route today.
3. If not → 403 `NOT_YOUR_ROUTE`, and nothing is saved.

A user with `TRIPS_RECORD_ANY` skips this route check. This is how the office corrects a wrong tap.

## How it looks in code

```java
public enum Permission { BUS_STATUS_VIEW, TRIPS_RECORD, TRIPS_RECORD_ANY, /* ... */ SETTINGS_EDIT }

public enum Role {
    OWNER(EnumSet.allOf(Permission.class)),
    OFFICE_ADMIN(EnumSet.of(Permission.BUS_STATUS_VIEW, Permission.TRIPS_RECORD_ANY /* ... */)),
    TRANSPORT_INCHARGE(/* ... */),
    ADMISSIONS_DESK(/* ... */),
    ATTENDANT(EnumSet.of(Permission.TRIPS_RECORD));

    private final Set<Permission> permissions;
    // constructor and getter
}
```

```java
@GetMapping("/api/v1/vehicles")
@PreAuthorize("hasAuthority('VEHICLES_VIEW')")
public List<VehicleResponse> list() { ... }
```

One unit test, `RolePermissionMatrixTest`, checks the enum against the table above, cell by cell. If someone changes the map by mistake, this test fails.

## What the React app gets

The login answer and `GET /api/v1/auth/me` return the list of permissions. React uses it only to hide menu items. The server still checks every request.

The Users and roles screen reads `GET /api/v1/roles`, which returns the same table as JSON.

## Rules for managing users

- Only `USERS_MANAGE` (the owner) can add, change or turn off users.
- To add a user you give **phone** and **role**. Name is optional.
- An `ATTENDANT` user must point at a `staff` row of type ATTENDANT. So the server knows the route.
- A phone number can belong to only one user.
- You cannot turn off or demote **the last active owner**. Answer: 409 `LAST_OWNER`.
- A user cannot turn off their own account.
- Turning off a user, or changing their role, does `token_version + 1`. Their token stops at once.
- Users are never deleted. Old records keep the name.
