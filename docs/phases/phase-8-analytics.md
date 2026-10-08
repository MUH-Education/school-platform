# Phase 8 — Analytics

## Goal

The owner chooses filters and sees numbers for graphs and a list of students with fee status. Nothing is typed here. This phase only reads.

## Screens this makes work

- **7. Analytics**

## Read first

- `docs/06-api.md` → Analytics
- `docs/03-data-model.md` → "How fee status is calculated"
- Questions C3 and C4 in `docs/08-decisions.md`

## Tables

None. This phase adds no table.

## Endpoints

`GET /analytics/summary`, `/analytics/fee-collection-by-month`, `/analytics/payment-by-occupation`, `/analytics/students-by-class`, `/analytics/students-by-village`, `/analytics/students`, `/analytics/students.csv`.

## Rules

1. **One filter for everything.** All seven URLs take the same query parameters and give numbers for the same set of students.

   | Parameter | Example | Meaning |
   |---|---|---|
   | `sessionId` | `1` | default: the current session |
   | `className` | `3` or a group like `1-5` | |
   | `village` | `Jakhal` | |
   | `routeId` | `4`, or `bus=NO` for children without bus | |
   | `occupation` | `FARMER_SMALL` | |
   | `feeStatus` | `DELAYED` | ON_TIME, DELAYED, DEFAULTED |

   Example: `?village=Jakhal&feeStatus=DELAYED` → every graph and the list show only children from Jakhal who are late.

2. Build one `StudentFilter` record and one method `AnalyticsBase.students(filter)` that returns the matching students with their fee status. Every endpoint starts from that list. This way two graphs can never disagree.
3. **This is small data.** At most 648 students. Load the matching students with their dues and payments, then count in Java. Do not write clever SQL. Simple code that is right beats fast code that is wrong.
4. **Summary:** number of students, number using the bus and the %, school fee collected % (paid ÷ due so far), bus fee collected %, number of students with something pending.
5. **Fee collection by month:** for each month of the session up to this month, and for each fee head:
   `collected % = money covering that month's dues ÷ that month's dues`. Payments cover the oldest dues first, as in the status rule.
   Example: April dues ₹10,00,000, covered ₹9,60,000 → 96%.
6. **Payment by occupation:** for each father's occupation: how many students are ON_TIME, DELAYED, DEFAULTED.
7. **Students by class:** count for each of the 15 classes, in class order, including classes with 0.
8. **Students by village:** count per village, biggest first, top 8 plus "others" (count and number of villages).
9. **List:** paged; columns name, class, village, father's occupation, bus route, school fee status, bus fee status, pending amount. Sortable by name, class and pending amount.
10. **CSV:** the same columns for all matching students, not only one page. First line is the header. UTF-8 with BOM so Excel opens Hindi names correctly.
11. **Privacy:** only `ANALYTICS_VIEW`. Each CSV download writes an `audit_log` row: who, when, which filters.
12. An empty result is not an error. Return zeros and empty lists.

## Tasks

- [x] 8.1 `StudentFilter` record with validation.
- [x] 8.2 `AnalyticsBase.students(filter)` (rules 2, 3).
- [x] 8.3 Summary (rule 4).
- [x] 8.4 `MonthlyCollectionCalculator` (pure Java) and its endpoint (rule 5).
- [x] 8.5 Payment by occupation (rule 6).
- [x] 8.6 Students by class and by village (rules 7, 8).
- [x] 8.7 List with paging and sorting (rule 9).
- [x] 8.8 CSV download with audit (rules 10, 11).
- [x] 8.9 Skipped. The owner answered C4: "student average graph" means students in each class, which is `/analytics/students-by-class` (task 8.6). No new endpoint.
- [x] 8.10 Swagger (API docs for the React developer). `springdoc-openapi-starter-webmvc-ui` 3.1.1, on in `dev` and `test` only. One `OpenApiConfig` (title, `bearerAuth`, shared `ApiError` schema), a tag, a summary and "Needs <PERMISSION>" on every endpoint, examples on the main request records, `OpenApiTest` and `OpenApiOffInProdTest`. No business logic and no URL changed.

## Tests that must pass

Use one fixed set of about 12 students made in the test, with known fees and payments.

- `summaryWithNoFilterCountsEveryActiveStudent`
- `villageFilterChangesEveryEndpointTheSameWay`
- `feeStatusFilterUsesTheSameRuleAsTheStudentPage`
- `monthlyCollectionIs96PercentInTheExample`
- `occupationCountsAddUpToTheTotal`
- `classesWithNoStudentsAreReturnedAsZero`
- `villagesBeyondTopEightAreGroupedAsOthers`
- `csvHasAllRowsNotOnlyOnePage`
- `csvDownloadIsAudited`
- `emptyFilterResultReturnsZerosNot404`
- Permission tests: `TRANSPORT_INCHARGE` and `ATTENDANT` get 403.

## Done when

- For the same filter, the number in the summary, the sum of the class bars, and the row count of the list are equal.
- The fee status of a student in the list is the same as on that student's own page.
- `/check-phase 8` passes.

## Out of scope

- Comparing two sessions.
- Charts drawn on the server. The server gives numbers; React draws.
- Marks and attendance, unless question C4 says so.
