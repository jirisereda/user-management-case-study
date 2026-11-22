# User Management Application

Small full-stack CRUD application: Spring Boot REST API + PostgreSQL + single-screen Angular client.

| Part     | Stack                                                            |
|----------|------------------------------------------------------------------|
| Backend  | Java 25, Spring Boot 4.1, Spring Data JPA, Bean Validation, Flyway |
| Database | PostgreSQL 17 running in a container (Podman / Docker)           |
| Frontend | Angular 22 (one standalone component, reactive forms)            |
| Tests    | JUnit 5, MockMvc, Testcontainers (real PostgreSQL)               |

## Run everything with one command

Requires Podman (or Docker) with compose.

```bash
podman compose up --build        # or: docker compose up --build
```

- UI: http://localhost:4200
- API: http://localhost:8080/api/users
- Swagger UI: http://localhost:8080/swagger-ui.html (spec: `/v3/api-docs`)

Stop with `podman compose down`.

## Run for development

Prerequisites: JDK 25, Node 24, Podman/Docker.

```bash
# 1. database
podman compose up -d postgres

# 2. backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. frontend (http://localhost:4200, proxies /api to :8080)
cd frontend
npm ci
npm start
```

Datasource defaults to `jdbc:postgresql://localhost:5432/users` (user/password `users`) and can be
overridden with `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## Tests

```bash
cd backend
./mvnw verify
```

The tests start PostgreSQL via Testcontainers, so a container runtime must be reachable.
With Podman on macOS:

```bash
podman machine start
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw verify
```

## Database choice

**PostgreSQL in a container.** It is the same database in tests, local development and compose,
so the unique constraint, Flyway migrations and SQL behaviour are tested for real rather than
against an in-memory approximation (H2). The schema is owned by Flyway
(`backend/src/main/resources/db/migration`); Hibernate only validates it (`ddl-auto: validate`).

## API

| Method | Path              | Body                         | Success | Errors                                    |
|--------|-------------------|------------------------------|---------|-------------------------------------------|
| GET    | `/api/users`      |                              | 200     |                                           |
| GET    | `/api/users/{id}` |                              | 200     | 404                                       |
| POST   | `/api/users`      | `{name, email}`              | 201 + `Location` | 400 validation, 409 duplicate email |
| PUT    | `/api/users/{id}` | `{name, version, email?}`    | 200     | 400 validation / email change, 404, 409 stale version |
| DELETE | `/api/users/{id}` |                              | 204     | 404                                       |

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details,
validation errors additionally contain a per-field `errors` map:

```json
{ "status": 400, "title": "Bad Request", "detail": "Validation failed",
  "errors": { "email": "must be a well-formed email address" } }
```

## Design decisions

- **Flat, single-feature structure** – entity, repository, service, one controller, DTO records,
  one exception handler. No extra layers or mappers; the scope doesn't justify them.
- **Email is immutable, enforced in three places:**
  1. `UserService.update` rejects a different email with **400** (an email equal to the stored one
     is accepted, so a client sending the whole object back still works). Silently ignoring the
     field was rejected as an option: the caller would believe the change succeeded.
  2. The JPA column is `updatable = false` as a safety net.
  3. The UI shows the email as a disabled field in edit mode and never sends it on update.
- **Email normalization** – trimmed and lower-cased, so `Alice@Example.com` and
  `alice@example.com` are treated as duplicates. Duplicates are checked in the service (clear
  409 message) and by a DB unique constraint (covers concurrent inserts, also mapped to 409).
- **Optimistic locking** – each user has a `version`; PUT must send the version the client last
  read, a stale one returns **409**. The service compares it explicitly, because Hibernate's
  `@Version` only checks the entity it loaded itself, not the version the client edited.
- **Input trimming** happens in the request records' compact constructors, i.e. *before* bean
  validation, so `" a@b.cz "` is valid input.
- **No CORS config** – the Angular dev server and nginx proxy `/api`, so the browser always talks
  to a single origin.

## How AI was used

The application was built with Claude Code (CLI agent); I acted as architect and reviewer.
Workflow: the agent read the assignment PDF and produced a plan, I decided the open points
(Java 25, PostgreSQL in Podman, which optional extensions), then it implemented the plan in
small steps, each verified (build, tests, curl, UI clicked through in a browser) before a commit.

Things caught during review / verification rather than accepted as generated:

- **Tests that never ran** – the first test class was named `*IT`, which Maven Surefire doesn't
  pick up; the build was green with `Tests run: 0`. Renamed so the tests run in `./mvnw verify`.
- **Real bug found by the tests** – an email with surrounding spaces failed `@Email` validation
  because trimming was done in the service, after validation. Moved trimming into the request
  records.
- **Test quality check** – the email-rule check was temporarily disabled in the service to confirm
  the key test actually fails (`expected 400 but was 200`), then restored.
- **Optimistic locking pitfall** – relying on `@Version` alone would not detect a stale client
  version; added the explicit comparison and a test for it.
- **Email rule** – a DTO without an `email` field would make Jackson silently ignore it; the rule
  is enforced explicitly instead.
- **Dependency versions** – generators resolve to the newest releases; versions published only
  days ago (Spring Boot 4.2.0, Angular 22.3.0, springdoc 3.2.1) were replaced with the previous,
  settled ones and Angular packages were pinned (npm `^` ranges had pulled in 22.3.0).

## Possible improvements

- **Concurrent edits over HTTP semantics** – expose the version as an `ETag` and require
  `If-Match` on PUT/DELETE (412 Precondition Failed) instead of a `version` field in the body;
  also protect DELETE against deleting a user that was changed meanwhile.
- **Further tests** – service-level unit tests for fast feedback; a `@WebMvcTest` slice for
  error mapping; concurrency test for the duplicate-email race (two parallel inserts);
  Flyway migration tests on upgrade; contract tests from the OpenAPI spec; frontend component
  tests and one Playwright e2e test of the create/edit/delete flow.
- **API** – pagination and sorting for the list, `PATCH` for partial updates, an `X-Request-Id`
  for tracing, typed problem `type` URIs so clients can branch on error kind instead of status.
- **Data** – audit columns (`created_at`, `updated_at`, who changed it), soft delete if user
  history matters, case-insensitive unique index (`citext` / `lower(email)`) if the original
  email casing should be preserved.
- **Operations** – Actuator health/readiness probes, structured logging, CI pipeline running
  `./mvnw verify` and the frontend build, image scanning, secrets outside `compose.yaml`.
