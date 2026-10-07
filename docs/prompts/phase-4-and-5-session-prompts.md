# Prompts for new sessions: Phase 4 and Phase 5

Use one new chat and one new session for each phase. Start Phase 5 only after Phase 4 is merged.
Before Phase 4: merge pull request #4 (Phase 3) into `main`. If it is not merged yet, tell the new session to start from branch `claude/affectionate-planck-a2r88d`.

---

## PROMPT 1: Phase 4 (Trips and bus status)

This repo is the Spring Boot backend for the school. Phases 0, 1, 2 and 3 are Done and merged into main. Now do Phase 4 only.

Environment check first:
- Run `java -version` and `docker ps`. This project needs Java 25 and a running Docker daemon.
- If Java 25 is missing, install it: `apt-get update && apt-get install -y openjdk-25-jdk-headless`, then `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- If the Docker daemon is not running, start it: `nohup dockerd > /tmp/dockerd.log 2>&1 &` and wait a few seconds. Then `docker pull postgres:17`.
- Maven Central sometimes answers 429 (too many requests). That is the network, not the code. Wait and retry. Do not change dependencies because of it.
- If both fail, stop and tell me. Do not switch to H2, a lower Java version, or Maven.

Before you write anything, read these files fully:
1. CLAUDE.md and .claude/rules/security.md
2. docs/02-architecture.md
3. docs/phases/README.md
4. docs/phases/phase-4-trips-bus-status.md and everything under "Read first" in it
   (docs/03-data-model.md Phase 4 table, docs/06-api.md Trips and bus status, docs/05-roles-permissions.md "attendant has one more check")
5. docs/08-decisions.md, especially part D (decisions of Phases 0 to 3)
Also read the existing code you must reuse:
- `student.StudentQueryService.onRoute(routeId, date)` and `RouteChild` (children on a route on a day)
- `route.AttendantRouteService.routeFor(userId, date)` (the attendant's route today)
- the `common`, `audit`, `route`, `staff`, `student` packages, and `src/test` helpers (AbstractIntegrationTest, MutableClock, addUser, tokenFor, login, bearer, EndpointSecurityTest)

Then do the Phase 4 tasks in order, one at a time:
- Order inside a task: migration, entity, repository, service, controller.
- After each task: run `./gradlew build -x test` (compile), tick the box in the phase file, make ONE git commit with a clear message, and push to the branch you were given.
- One tap is saved once: the unique key `(student_id, service_date, event_type)` must make a repeated tap update the same row.
- The attendant sees only their own route. Look up the route in the database. Never trust a `routeId` or `studentId` from the client. Wrong route gives 403 `NOT_YOUR_ROUTE` and nothing is saved. `TRIPS_RECORD_ANY` skips this check.
- `GET /auth/me` still sends `route: null` since Phase 2. Fill it from `AttendantRouteService` in this phase (it is noted in docs/08-decisions.md).
- The attendant never sees the student list or parents' phone numbers.
- Features talk only through services. Entities hold ids, not objects. Never return a JPA entity. Time comes from the injected `Clock`. Never log a full phone number, an OTP or a JWT.
- Do not start Phase 5. Do not send any SMS. Phase 5 builds messaging.

Tests: Write the tests listed in the phase file, test first where the phase file says so. Every new endpoint needs "no token gives 401" and "wrong role gives 403", and goes into the list in `EndpointSecurityTest`.
(Note: the Phase 3 tests listed in docs/phases/phase-3-students-admission.md were deferred and are not written yet. Do not write them in this session unless I ask.)

Rules while you work:
- If the plan and reality disagree, or you must decide something the docs do not cover, stop and ask me ONE clear question. If it is a small choice with an obvious answer, decide it yourself and write one line in docs/08-decisions.md part D.
- Talk to me in simple English, short sentences, with a real example (see CLAUDE.md). After each task say in 3 to 5 lines: what you built, how to try it, what is next.

When all tasks are done, go through the "Done when" list in the phase file. Run the steps with the app running and the dev data loaded (`./gradlew bootRun`, or the same with `SPRING_DOCKER_COMPOSE_ENABLED=false` and a PostgreSQL you started yourself). Tell me pass or fail for each line. Set Phase 4 to Done and Phase 5 to In progress in docs/phases/README.md only if every line passes.

---

## PROMPT 2: Phase 5 (SMS and WhatsApp)

This repo is the Spring Boot backend for the school. Phases 0 to 4 are Done and merged into main. Now do Phase 5 only.

Environment check first:
- Run `java -version` and `docker ps`. This project needs Java 25 and a running Docker daemon.
- If Java 25 is missing: `apt-get update && apt-get install -y openjdk-25-jdk-headless`, then `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
- If Docker is not running: `nohup dockerd > /tmp/dockerd.log 2>&1 &`, wait, then `docker pull postgres:17`.
- Maven 429 errors are the network. Wait and retry. Do not change dependencies because of it.
- If both fail, stop and tell me. Do not switch to H2, a lower Java version, or Maven.

Before you write anything, read these files fully:
1. CLAUDE.md and .claude/rules/security.md
2. docs/02-architecture.md
3. docs/phases/README.md
4. docs/phases/phase-5-sms-whatsapp.md and everything under "Read first" in it
   (docs/07-messaging.md fully, docs/03-data-model.md Phase 5 tables, docs/06-api.md Messages, docs/04-login-otp-jwt.md for the OTP senders)
5. docs/08-decisions.md, especially part D and the open questions C1 (SMS company) and C9 (absent SMS)
Also read the existing code you must connect to:
- `auth.OtpSender`, `LogOtpSender`, `OtpDeliveryService` (the OTP channels: the `prod` profile cannot start until real senders exist)
- the Phase 4 trip code that records a tap (each tap must create one outbox row per parent phone)
- `student.GuardianService` and `StudentGuardian.smsEnabled` (a phone with SMS off gets no message)
- `Student.gender` (boy text or girl text, 8 templates)

Then do the Phase 5 tasks in order, one at a time:
- Order inside a task: migration, entity, repository, service, controller.
- After each task: run `./gradlew build -x test` (compile), tick the box in the phase file, make ONE git commit, and push to the branch you were given.
- **Never send a real SMS or WhatsApp message from a test or from dev.** Only the `log` provider runs outside `prod`. A real provider needs my answer to question C1 first. If the phase needs it, stop and ask me.
- Parent SMS goes through the queue table `message_outbox`. One tap gives one SMS per phone. The partial unique index must make a repeated tap send nothing twice.
- Never log a full phone number, an OTP, a JWT, or a provider key. Provider keys come from environment variables and have no default in `prod`.
- Features talk only through services. Entities hold ids, not objects. Time comes from the injected `Clock`.
- Do not start Phase 6.

Tests: Write the tests listed in the phase file. Every new endpoint needs "no token gives 401" and "wrong role gives 403", and goes into `EndpointSecurityTest`. No test sends a real message.
(Note: the Phase 3 tests, and any Phase 4 tests I deferred, may still be missing. Do not write them in this session unless I ask.)

Rules while you work:
- If the plan and reality disagree, or you must decide something the docs do not cover, stop and ask me ONE clear question. For a small obvious choice, decide it yourself and write one line in docs/08-decisions.md part D.
- Talk to me in simple English, short sentences, with a real example. After each task say in 3 to 5 lines: what you built, how to try it, what is next.

When all tasks are done, go through the "Done when" list. Run the steps with the app running and the dev data loaded, with the `log` provider. Tell me pass or fail for each line. Set Phase 5 to Done and Phase 6 to In progress in docs/phases/README.md only if every line passes.
