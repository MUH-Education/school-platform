This repo is the Spring Boot backend for the school. Phases 0 to 7 are Done and merged into main.
Do Phase 8 only (Analytics), and then add Swagger (API docs) as the last task.

STOP FIRST: open docs/08-decisions.md part C. Check C3 (can Analytics show a child's name next to fee
status and father's occupation?) and C4 (what does "student average graph" mean?).
- If C4 is still open, ask me ONE clear question with a recommended default, and wait for my answer
  before writing code. The default is "students in each class" (that is already /analytics/students-by-class),
  so task 8.9 is skipped.
- If I do not answer C3 and C4, use the defaults written in part C and add one line to part D saying so.
  C3 default: yes, but only OWNER, OFFICE_ADMIN and ADMISSIONS_DESK (the roles with ANALYTICS_VIEW).

First check the environment: `java -version` (needs Java 25) and `docker ps`.
- If Java 25 is missing: `apt-get update && apt-get install -y openjdk-25-jdk-headless`
  and set JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64.
- If Docker is not running: `setsid nohup dockerd > /tmp/dockerd.log 2>&1 < /dev/null &`
  (check `docker ps` again before every test run, it can stop).
- Maven 429 errors are the network: wait and retry. If it still fails, stop and tell me.
  Do not use H2, a lower Java, or Maven.

Read fully: CLAUDE.md, .claude/rules/security.md, docs/02-architecture.md, docs/phases/README.md,
docs/phases/phase-8-analytics.md and its "Read first" files (docs/06-api.md Analytics,
docs/03-data-model.md "How fee status is calculated"), and docs/08-decisions.md part D (Phase 7 lines).

Reuse, do not rewrite:
- FeeStatusCalculator (pure Java, fee package) is THE fee status rule. FeeStatusService.statuses(ids)
  already gives status for many children. The list in Analytics must show the same status as
  GET /students/{id}/fees. Never write the maths a second time.
- StudentQueryService, ClassNames, FatherOccupation, PageResponse, ApiException, AuditService,
  CurrentUser, java.time.Clock (never LocalDateTime.now()). Money is BigDecimal, never double.
- A feature calls another feature only through its SERVICE, never its repository. The analytics
  package must not use StudentRepository or FeePaymentRepository. Add small read methods to the
  student and fee services if you need data (like Phase 7 did with StudentBasics).
- Set the work branch to the one my session names. If my last PR was merged, restart that branch
  from the latest main first.

Do the tasks in the order of the phase file, one at a time (8.1 to 8.8, then 8.9 only if C4 was
answered with something new, then task 8.10 Swagger below):
migration (none in this phase), entity (none), repository/service, controller, then tests.
After each task: compile with ./gradlew build -x test, run the tests of that task, tick the box in
the phase file, make ONE commit, push. Do not batch two tasks into one commit.

Rules to keep:
- Server checks permission on every request: ANALYTICS_VIEW only, never roles. Every new URL gets
  "no token -> 401" and "wrong role -> 403" lines in EndpointSecurityTest (TRANSPORT_INCHARGE and
  ATTENDANT get 403).
- One StudentFilter record, one AnalyticsBase.students(filter). Every endpoint starts from that list,
  so two graphs can never disagree. Small data (max 648 students): load, then count in Java. No clever SQL.
- Never trust sessionId, routeId or any id from the client: look it up in the database; 404 or 400
  for an unknown session or route. className accepts a group like "1-5"; validate it with ClassNames.
- Monthly collection: put the maths in one pure class (MonthlyCollectionCalculator), no Spring, and test
  the example (April dues Rs 10,00,000, covered Rs 9,60,000 -> 96%) with a fixed Clock.
- An empty result is zeros and empty lists, never 404.
- CSV: all matching rows (not one page), header line first, UTF-8 with BOM. Beware CSV injection:
  a cell that starts with = + - @ must be prefixed with a quote. Each download writes an audit_log
  row (who, when, which filters).
- No full phone numbers in logs or in the CSV (the list has no phone column anyway).
- Do not start Phase 9.

Write the tests the phase file lists ("Tests that must pass") using one fixed set of about 12 students
made in the test, with known fees and payments. Run ./gradlew test before the last commit. Fix until green.

TASK 8.10 - SWAGGER (API docs for the React developer). Add it to docs/phases/phase-8-analytics.md as
task 8.10 (it is not in the file yet) and do it LAST, as its own commit.
- Library: springdoc-openapi, the version made for Spring Boot 4 (Spring Framework 7). Do not guess the
  coordinates or version: check the springdoc migration notes and use the one that really supports
  Boot 4. If it will not build, stop and tell me. Add ONE line in docs/08-decisions.md part D saying why
  this library was added (CLAUDE.md rule: no library without a reason).
- ONLY in the dev and test profiles. In prod, the docs and the UI are OFF (springdoc.api-docs.enabled=false,
  springdoc.swagger-ui.enabled=false). A test must prove it: with the prod-like setting,
  /v3/api-docs and /swagger-ui/index.html give 404 or 401, never the page.
- Security rules still hold: the open URLs in .claude/rules/security.md are only otp/request, otp/verify
  and /actuator/health. Swagger's own two paths (/v3/api-docs/**, /swagger-ui/**) may be open ONLY when
  the docs are switched on (dev/test). Update .claude/rules/security.md with this one exception, and
  add the line to docs/08-decisions.md. Every API call made from Swagger still needs a real token and
  still gets 401/403 as normal. Swagger must never be a way around permissions.
- One OpenAPI config class (title "MUH Jain Global School API", version, short description, the JWT
  "bearerAuth" scheme so the "Authorize" button takes the token). Document the shared error shape
  {"error","message","fields"} once, as a reusable schema.
- Tag every controller by feature (Auth, Users, Vehicles, Staff, Routes, Students, Admissions, Trips,
  Bus status, Messages, Enquiries, Fees, Analytics, Settings). On every endpoint add a short
  @Operation summary and write the needed permission in the description, e.g. "Needs FEES_EDIT".
  Mark the 3 open URLs as not needing the token. Use simple English.
- Record examples: add @Schema(example = ...) on the main request records (admission, fee plan, payment,
  correction, analytics filter) using the examples already written in the Javadoc.
- Do NOT change any business logic or any URL for Swagger. Controllers return records, never entities.
- Tests: OpenApiTest in test profile: /v3/api-docs returns 200 and lists every /api/v1 path of the
  analytics and fees controllers; every operation except the 3 open ones declares bearerAuth; the
  prod-like check above. EndpointSecurityTest stays green.
- Write in the README (or docs/02-architecture.md) 3 lines: how to open
  http://localhost:8080/swagger-ui/index.html, how to get a token (POST otp/request, read the code in the
  console log in dev, POST otp/verify), and how to click Authorize.

At the end, run the "Done when" steps with the app running (docker postgres + java -jar, as in the
Phase 7 check; the dev data has fee plans in all three statuses):
- For the same filter, the summary number, the sum of the class bars and the row count of the list are equal.
- The fee status of a student in the list is the same as on that student's own page.
- /swagger-ui/index.html opens in dev, "Authorize" with an owner token works, and one call
  (GET /analytics/summary) returns 200 from Swagger.
- The same jar started with the prod profile settings (or the property switched off) does not serve Swagger.
Tell me pass or fail for each line. Note: /check-phase does not exist in this repo, so check the
"Done when" lines by hand and say so. Set Phase 8 to Done and Phase 9 to In progress in
docs/phases/README.md only if all pass.

If the docs and reality disagree, ask me one clear question. For a small obvious choice, decide it
and write one line in docs/08-decisions.md part D.

Talk in simple English with a real example. After each task say in 3 to 5 lines: what you built,
how to try it, what is next.
