# Aviation Hexagonal

[![Scala CI](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml/badge.svg)](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml)

A Scala 3 / ZIO multi-module application built from scratch to demonstrate **Hexagonal Architecture**
(ports & adapters). The domain models a small slice of the aviation world — **Country → Airport →
Airline → Aircraft → Route → Flight**, plus FlightInstance — with a Kafka outbox pattern for
domain events still being wired in.

## Tech stack

- **Scala 3** (LTS) + **SBT 1** multi-module build
- **ZIO** for functional effects, **ZIO HTTP** for the server, **Tapir** for endpoint definitions
  and code-first OpenAPI generation
- **Quill** over **PostgreSQL** persists every wired entity — `Country`, `Airport`, `Airline`,
  `Aircraft`, `Route`, `Flight`, plus the auth `User`/revoked-token tables. Only route–airline
  links and `FlightInstance` are still in-memory stubs. Flyway migrations run in-process at startup
- **JWT** bearer authentication (`jwt-scala`, HS256) with **bcrypt** password hashing — every
  business endpoint requires a token
- **ZIO Kafka** (4.x) outbox relay exists but isn't wired in yet
- **Circe** for JSON, **ZIO Logging** (SLF4J/Logback) for logging
- **ZIO Prelude** `Newtype`s with validated smart constructors for the aviation codes

See [CLAUDE.md](./CLAUDE.md) for the module dependency graph and wiring status;
versions are pinned in [`project/Versions.scala`](./project/Versions.scala).

## Running locally

Requires **Java 21 LTS** (ZIO is only certified for Java 17/21; Java 25 silently breaks the test
framework). Start Postgres and Kafka:

```bash
docker compose up -d
```

Build and run the server as a fat JAR (`java -jar` runs the OpenAPI spec generator instead —
use `java -cp` to start the server):

```bash
sbt -Dcoverage=false "clean; bootstrap/assembly"
JAR=$(find bootstrap/target -name "bootstrap-assembly-*.jar" | sort | tail -1)
java -cp "$JAR" dev.cmartin.aerohex.bootstrap.Main
```

`-Dcoverage=false` keeps scoverage instrumentation out of the jar (coverage is on by default
for every module, and `coverageOff` alone can't turn it off — see the comment in `build.sbt`).

On startup the app runs Flyway migrations in-process, before the HTTP port binds — new
`V*.sql` files under `infrastructure/migration/` apply automatically, and a migration failure
aborts startup. Set `FLYWAY_MIGRATE_ON_START=false` to skip.

> **One-time, per machine:** if your dev database predates migrate-on-start (hand-applied
> schema, no `flyway_schema_history` table), reset it once — `docker compose down -v &&
> docker compose up -d`, start the app (applies `V1`–`V20` from scratch), then re-seed the
> `plans/seed-data-*.sql` scripts — at least countries/airports and users (needed to log in):
> `docker exec -i aero-hex-ai-postgres-1 psql -U aviation -d aviation < plans/seed-data-countries-airports.sql`
> `docker exec -i aero-hex-ai-postgres-1 psql -U aviation -d aviation < plans/seed-data-users.sql`

Verify it's up:

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health/live    # → 200
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health/ready   # → 200 (503 if Postgres is unreachable)
```

Swagger UI is served at `http://localhost:8080/docs`. Get a token from
`POST /api/v1/auth/login` (the dev user comes from `plans/seed-data-users.sql`) and send it as
`Authorization: Bearer <token>`.

## API status

**Auth** (login, logout), **Countries** (CRUD + search), **Airports** (CRUD + search,
list-by-country), **Airlines** (CRUD + search), **Aircraft** (CRUD), **Flights** (CRUD) and
**Routes** (list with origin/destination filters, create) are implemented end-to-end against real
Postgres. Flight Instances and the route–airline links are stubbed. Liveness/readiness probes are
available at `/health/live` and `/health/ready` (unversioned, no `/api/v1` prefix). See
[docs/api/endpoint-status.md](./docs/api/endpoint-status.md) for the per-endpoint status table.

## Development

See [CLAUDE.md](./CLAUDE.md#validation) for build/test/coverage commands, and the
[Versioning policy](./CLAUDE.md#versioning-policy) and
[Hexagonal layer conventions](./CLAUDE.md#hexagonal-layer-conventions) sections for architectural
conventions.

Unit tests (`sbt test`) run against in-memory stubs / a Tapir stub server and never touch a
real database. A separate opt-in suite exercises the persistence layer against a real Postgres
started via Testcontainers (requires Docker):

```bash
sbt integrationTests/test
```

See [CLAUDE.md](./CLAUDE.md#integration-tests-opt-in-real-postgres) for coverage and setup details.
`master-data-sync` (bulk import of reference data, see `plans/masterdata/`) is opt-in the same
way; run its tests with `sbt masterDataSync/test`.

A third, opt-in layer exercises the actual running app end to end: real Postgres, the real HTTP
server, and the Postman collection's `E2E — ...` folders run against it via Newman. Use the
`run-e2e-tests` skill (Claude Code) or see [CLAUDE.md](./CLAUDE.md#validation) for the manual steps.

