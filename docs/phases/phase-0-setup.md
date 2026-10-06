# Phase 0 — Project setup

## Goal

An empty Spring Boot 4 project that starts, connects to PostgreSQL, runs one Flyway file, and has one passing test.

## Screens this makes work

None. This is the foundation.

## Read first

- `CLAUDE.md`
- `docs/02-architecture.md`

## What you need on your laptop

| Tool | Version | Check with |
|---|---|---|
| JDK | 25 | `java -version` |
| Docker Desktop | any recent | `docker ps` |
| Git | any | `git --version` |

You do not install Gradle. The project carries its own (`./gradlew`).

Docker is needed for two things: PostgreSQL while you develop, and PostgreSQL inside the tests.

## Create the project

Go to **start.spring.io** and choose:

| Field | Value |
|---|---|
| Project | Gradle - Kotlin |
| Language | Java |
| Spring Boot | the newest 4.x that is not SNAPSHOT or RC |
| Group | `com.muhjain` |
| Artifact | `school-admin` |
| Package name | `com.muhjain.school` |
| Packaging | Jar |
| Java | 25 |

Dependencies to add:

| In the list it is called | Why |
|---|---|
| Spring Web | REST API |
| Spring Data JPA | Database access |
| PostgreSQL Driver | Database driver |
| Flyway Migration | Creates tables from SQL files |
| Validation | Checks request fields |
| Spring Security | Login and permissions |
| OAuth2 Resource Server | Reads the JWT |
| Spring Boot Actuator | `/actuator/health` |
| Docker Compose Support | Starts PostgreSQL when you run the app |
| Testcontainers | PostgreSQL inside tests |

Press Generate. Unzip. Copy `CLAUDE.md`, `.claude/` and `docs/` from this plan folder into the project root.

Why start.spring.io and not a hand-written build file? Spring Boot 4 renamed several starters. The website always gives the right names for the version you pick.

## Tasks

- [x] 0.1 Create the project as described above. Run `./gradlew build`. It must pass before you change anything.
- [x] 0.2 Create a Git repository. Add a `.gitignore` (the generated one, plus `data/`, `.env`, `CLAUDE.local.md`). First commit.
- [x] 0.3 Edit the generated `compose.yaml` so it has: one `postgres` service, image `postgres:17`, database `school`, user `school`, password `school`, port 5432, and a named volume so data survives a restart.
- [x] 0.4 Write `application.yml` with profiles `dev` (default), `test`, `prod` and the `app.*` block from `docs/02-architecture.md`. Set `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.
- [x] 0.5 Add `src/main/resources/db/migration/V0__baseline.sql` with one harmless line: `select 1;`. Start the app. Flyway must report 1 migration applied.
- [x] 0.6 Create package `common` with:
  - `ApiException` and `GlobalExceptionHandler` (the error format from `docs/02-architecture.md`)
  - `ClockConfig` (a `Clock` bean in zone `Asia/Kolkata`)
  - `PhoneNumbers.normalize(String)` → `+91XXXXXXXXXX` or throws
  - `PageResponse<T>` record for paged lists
- [x] 0.7 Add a temporary `SecurityConfig` that allows `/actuator/health` and blocks everything else with 401. Phase 1 replaces it.
- [ ] 0.8 Create `AbstractIntegrationTest`: starts one PostgreSQL Testcontainer for all tests, `@SpringBootTest`, `@AutoConfigureMockMvc`, profile `test`.
- [ ] 0.9 Write the tests listed below.
- [ ] 0.10 Write `README.md`: how to run, how to test, in 10 lines.
- [ ] 0.11 Outside the code: choose the SMS and WhatsApp company and start DLT registration (question C1). It takes one to two weeks and Phase 5 needs it.

## Tests that must pass

- `ApplicationStartsTest`: the Spring context loads against Testcontainers PostgreSQL.
- `HealthEndpointTest`: `GET /actuator/health` → 200 without a token.
- `EverythingElseIsLockedTest`: `GET /api/v1/anything` → 401 without a token.
- `PhoneNumbersTest`:
  - `98123 45678` → `+919812345678`
  - `09812345678` → `+919812345678`
  - `+91-98123-45678` → `+919812345678`
  - `12345` → error
  - `5812345678` (does not start with 6 to 9) → error
- `GlobalExceptionHandlerTest`: an `ApiException` becomes JSON with `error` and `message`.

## Done when

- `./gradlew build` is green on your laptop.
- `./gradlew bootRun` starts PostgreSQL by itself and the app answers `{"status":"UP"}` on `http://localhost:8080/actuator/health`.
- The table `flyway_schema_history` exists in the database.
- The project is in Git.

## Out of scope

No entities, no login, no business code. If you are writing a `Vehicle` class, you are in the wrong phase.
