# School Admin Backend

Spring Boot backend for MUH Jain Global School. You need **JDK 25** and **Docker** (running).

1. Run the app: `./gradlew bootRun`. It starts PostgreSQL from `compose.yaml` by itself, then the app on port 8080.
2. Check it: open `http://localhost:8080/actuator/health`. You should see `"status":"UP"`.
3. Look at the database: `localhost:5432`, database `school`, user `school`, password `school`.
4. Run all tests: `./gradlew test`. Tests start their own PostgreSQL in Docker (Testcontainers).
5. Run one test class: `./gradlew test --tests "*PhoneNumbersTest"`.
6. Build the jar: `./gradlew build`. The jar is in `build/libs/`.

### API docs (Swagger), dev only

1. With the app running (`./gradlew bootRun`), open `http://localhost:8080/swagger-ui/index.html`. The raw docs are at `http://localhost:8080/v3/api-docs`. In `prod` both are switched off.
2. Get a token: in Swagger call `POST /api/v1/auth/otp/request` with your phone, read the 6-digit code in the console log of the app (dev prints it), then call `POST /api/v1/auth/otp/verify` with the phone and the code. Copy the `token` from the answer.
3. Click **Authorize** (top right), paste the token (without the word `Bearer`), click Authorize, close. Now every call, for example `GET /api/v1/analytics/summary`, sends the token. Swagger is not a way around permissions: a wrong role still gets 403.

The plan: `START-HERE.md`, `CLAUDE.md`, and the phase list in `docs/phases/README.md`.
