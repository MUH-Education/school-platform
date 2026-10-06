# School Admin Backend — instructions for Claude

Backend for MUH Jain Global School (Tohana, Haryana). One Spring Boot app serves the admin web app (React) and the bus attendant phone app. Parents have no app; they only get SMS.

## How to talk to the owner

- The owner is a Spring Boot developer. He is **not strong in English**.
- Use **simple English, short sentences, and a real example** for every idea.
  Example: do not say "the endpoint is idempotent". Say "if the phone sends the same tap twice, the server saves it once".
- After each task, say in 3 to 5 lines: what you built, how to try it, what is next.

## Stack (do not change without asking)

| Thing | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.x (Spring Framework 7, Spring Security 7) |
| Build | Gradle 9.x with Kotlin DSL (`build.gradle.kts`), use the wrapper `./gradlew` |
| Database | PostgreSQL 17 or newer, for every entity |
| Schema changes | Flyway SQL files only |
| Data access | Spring Data JPA (Hibernate), `ddl-auto=validate` |
| Login | Phone number + OTP (WhatsApp or SMS), then one JWT |
| JSON | Jackson 3 (package `tools.jackson`, not `com.fasterxml.jackson`) |
| Tests | JUnit 5, MockMvc, Testcontainers PostgreSQL |

Not used: Lombok, MapStruct, Kafka, Redis, microservices, H2.

## Commands

```bash
./gradlew bootRun          # start the app (starts PostgreSQL from compose.yaml, needs Docker)
./gradlew test             # all tests (needs Docker for Testcontainers)
./gradlew test --tests "*AuthFlowTest"   # one test class
./gradlew build            # compile + test + jar
```

## Where the plan lives

Read the document for the area you are working on **before** writing code.

| File | What it answers |
|---|---|
| `docs/01-overview.md` | What we build, which screen needs which module |
| `docs/02-architecture.md` | Packages, layers, error format, naming |
| `docs/03-data-model.md` | Every table and column, with example rows |
| `docs/04-login-otp-jwt.md` | Login flow step by step |
| `docs/05-roles-permissions.md` | Which role can do what |
| `docs/06-api.md` | Every URL |
| `docs/07-messaging.md` | Parent SMS, WhatsApp, templates, queue |
| `docs/08-decisions.md` | Decisions made, and questions still open |
| `docs/phases/README.md` | The phase list and the current status |
| `docs/phases/phase-N-*.md` | Tasks for one phase |

## How to work

1. Open `docs/phases/README.md`. Find the phase marked **In progress**.
2. Open that phase file. Do the **first task that is not ticked**. Do only that task.
3. Order inside every task: migration → entity → repository → service → controller → tests.
4. Run `./gradlew test`. Fix until green.
5. Tick the task box (`- [x]`) in the phase file.
6. If you had to decide something the docs do not cover, add one line to `docs/08-decisions.md`.
7. If the code and a doc disagree, stop and ask. Do not silently pick one.

Never start a new phase while the current phase has a failing "Done when" check.

## Architecture rules

- **Package by feature**: `com.muhjain.school.<feature>`. Features: `auth`, `user`, `vehicle`, `staff`, `route`, `student`, `trip`, `messaging`, `enquiry`, `fee`, `analytics`, `audit`, `common`.
- Inside a feature: `XController` → `XService` → `XRepository`. A controller never calls a repository.
- A feature may call another feature's **service**, never its repository.
- API input and output are Java `record` classes named `...Request` and `...Response`. **Never return a JPA entity from a controller.**
- Entities are plain classes with getters and setters. Relations to other features are stored as ids (`Long routeId`), not as `@ManyToOne` objects.
- All URLs start with `/api/v1/`.
- Every write method in a service is `@Transactional`. Read methods use `@Transactional(readOnly = true)`.
- Dates: store `timestamptz` in UTC. A "school day" (`service_date`) is a `LocalDate` in zone `Asia/Kolkata`. Never use `LocalDateTime.now()`; inject `java.time.Clock`.
- Phone numbers are stored as `+91XXXXXXXXXX`. Always pass input through `PhoneNumbers.normalize()`.
- Money is `BigDecimal` in Java and `numeric(12,2)` in SQL. Never `double`.

## Error format

Every error response has this shape:

```json
{ "error": "OTP_INVALID", "message": "The code is wrong or too old.", "fields": null }
```

`error` is a stable code the React app can switch on. `fields` is filled only for validation errors (HTTP 400).

## Security rules (never break these)

1. **The server checks permission on every request.** Hiding a button in React is not security.
2. **Never trust an id from the client.** Example: an attendant sends `routeId=7`. Look up the attendant's own route in the database and compare.
3. **Never log** an OTP, a JWT, or a full phone number. Log phones as `+91XXXXXX4321`.
4. **OTP codes are stored hashed**, never as plain text.
5. Every endpoint has a test for "no token → 401" and "wrong role → 403".
6. Secrets (JWT key, provider keys) come from environment variables. Never commit them.

More detail: `.claude/rules/security.md`.

## Spring Boot 4 things that are different from Boot 3

- Web starter is `spring-boot-starter-webmvc` (not `-web`).
- Flyway needs `spring-boot-starter-flyway` plus `org.flywaydb:flyway-database-postgresql`.
- JWT support is `spring-boot-starter-security-oauth2-resource-server`.
- `@MockBean` is gone. Use `@MockitoBean`.
- `@SpringBootTest` does not give MockMvc by itself. Add `@AutoConfigureMockMvc`.
- Jackson is version 3: imports start with `tools.jackson`. Annotations stay in `com.fasterxml.jackson.annotation`.

If a class or starter name is not found, check the Spring Boot 4 migration guide before guessing.

## Do not

- Do not edit a Flyway file that has already run. Add a new `V<n>__name.sql`.
- Do not add a library without saying why in `docs/08-decisions.md`.
- Do not build things marked "Out of scope" in a phase file.
- Do not send a real SMS or WhatsApp message from a test.
