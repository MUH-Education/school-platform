# School Admin Backend

Spring Boot backend for MUH Jain Global School. You need **JDK 25** and **Docker** (running).

1. Run the app: `./gradlew bootRun`. It starts PostgreSQL from `compose.yaml` by itself, then the app on port 8080.
2. Check it: open `http://localhost:8080/actuator/health`. You should see `"status":"UP"`.
3. Look at the database: `localhost:5432`, database `school`, user `school`, password `school`.
4. Run all tests: `./gradlew test`. Tests start their own PostgreSQL in Docker (Testcontainers).
5. Run one test class: `./gradlew test --tests "*PhoneNumbersTest"`.
6. Build the jar: `./gradlew build`. The jar is in `build/libs/`.

The plan: `START-HERE.md`, `CLAUDE.md`, and the phase list in `docs/phases/README.md`.
