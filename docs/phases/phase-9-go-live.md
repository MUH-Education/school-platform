# Phase 9 — Go live

## Goal

The backend runs on a server in India, with real data, backups, and a second person who can keep it running.

You can do this phase twice: once after Phase 5 (transport system live) and again after Phase 8.

## Read first

- `.claude/rules/security.md`
- Question C8 in `docs/08-decisions.md`

## What you need (not code)

| Thing | Notes |
|---|---|
| A server in India | A small one is enough: 2 CPU, 2 to 4 GB memory |
| Managed PostgreSQL in India | With daily backup and restore to a point in time |
| A domain name and HTTPS | For example `api.<school-domain>` |
| An S3-style bucket in India | For student photos |
| Provider keys | SMS and WhatsApp, from Phase 5 |

## Environment variables in production

| Name | Example | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://host:5432/school` | |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | | |
| `APP_JWT_SECRET` | 32+ random bytes | make with `openssl rand -base64 48` |
| `APP_OTP_SECRET` | 32+ random bytes | a different value |
| `APP_OWNER_PHONE` | your phone | only used when no user exists |
| provider keys | | names depend on the provider |

Never put these in Git. Keep one written copy in a safe place.

## Tasks

### Make it ready

- [ ] 9.1 `application-prod.yml`: no secrets in the file, only `${ENV_NAME}` references. `server.forward-headers-strategy` set so the real client IP is seen behind a proxy (the OTP limit per IP needs it).
- [ ] 9.2 CORS: allow only the web app's address. Or build the React app into the jar's `static` folder so there is one address and no CORS at all.
- [ ] 9.3 `S3PhotoStorage` behind the `PhotoStorage` interface.
- [ ] 9.4 Logging: JSON lines, one line per request with user id, URL, status, time taken. No phone numbers, no OTP, no token.
- [ ] 9.5 A `Dockerfile` (or a systemd unit file) and a short `docs/deploy.md`: how to deploy, how to roll back, in numbered steps.
- [ ] 9.6 Error tracking (for example Sentry) and an uptime checker that calls `/actuator/health` every minute and alerts your phone.
- [ ] 9.7 Turn off the dev data loader in `prod` (it must have `@Profile("dev")`; add a test that proves it).

### Security check before real data

- [ ] 9.8 Run every "no token → 401" and "wrong role → 403" test. All green.
- [ ] 9.9 By hand, with two real attendant logins: try to open the other route. Must fail.
- [ ] 9.10 By hand: turn a user off, then use their old token. Must fail.
- [ ] 9.11 Search the logs of one test day for a 6-digit OTP, a token, and a full phone number. None may appear.
- [ ] 9.12 Photo URL without a token → 401.
- [ ] 9.13 Ask for 6 OTPs in an hour for one phone → the 6th is refused.

### Data

- [ ] 9.14 Enter the real vehicles, staff, routes and stops through the API or screens.
- [ ] 9.15 Import the real students with `dryRun=true`, fix the lines, import for real.
- [ ] 9.16 Ask each class teacher to check their class list and the parents' phone numbers once. A wrong number sends a child's SMS to a stranger.
- [ ] 9.17 Create the real users: phone and role only.

### Backups

- [ ] 9.18 Daily database backup is on. Photos bucket has versioning on.
- [ ] 9.19 **Restore test:** restore last night's backup into a new empty database, start the app against it, log in. Write down how long it took. A backup that was never restored is not a backup.

### People

- [ ] 9.20 A second trusted person has: server access, database access, the env values, and has read `docs/deploy.md`.
- [ ] 9.21 Parents are told in writing which SMS they will get, and the admission form has the line "I agree to receive bus SMS about my child on this number."

### Trial

- [ ] 9.22 Run **one route for 5 school days** with the real attendant. Sit in the bus on day 1.
- [ ] 9.23 After 5 days check: every child who travelled got every SMS; no parent got a wrong one; the attendant can use the app without help.
- [ ] 9.24 Fix what the trial found. Then turn on all routes.

## Done when

- The app answers on HTTPS at its real address.
- A restore test was done and timed.
- One route ran 5 days with no missed and no wrong SMS.
- A second person can deploy and restore without you.

## Out of scope

- More than one server, load balancers, Kubernetes. One server is right for this size.
- A staging environment. Your laptop with Docker is the staging for now.
