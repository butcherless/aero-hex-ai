# Aviation Hexagonal

[![Scala CI](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml/badge.svg)](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml)

A Scala 3 / ZIO multi-module application built from scratch to demonstrate **Hexagonal Architecture**
(ports & adapters). The domain models a small slice of the aviation world — **Country → Airport →
Airline → Route** — with a Kafka outbox pattern for domain events still being wired in.

## Tech stack

- **Scala 3** (LTS) + **SBT 2** multi-module build
- **ZIO** for functional effects, **ZIO HTTP** for the server, **Tapir** for endpoint definitions
  and code-first OpenAPI generation
- **Quill** over **PostgreSQL** persists `Country`, `Airport`, and `Airline` (the only repositories
  wired to a real database so far); Doobie exists in code, kept schema-consistent, but isn't wired
  into the running app. Flyway migrations run in-process at startup
- **ZIO Kafka** outbox relay exists but isn't wired in yet
- **Circe** for JSON, **ZIO Logging** (SLF4J/Logback) for logging

See [CLAUDE.md](./CLAUDE.md) for the full module dependency graph, wiring status, and
per-dependency versions.

## Running locally

Requires **Java 21 LTS** (ZIO is only certified for Java 17/21; Java 25 silently breaks the test
framework). Start Postgres and Kafka:

```bash
docker compose up -d
```

Build and run the server as a fat JAR (`java -jar` runs the OpenAPI spec generator instead —
use `java -cp` to start the server):

```bash
sbt ";clean;bootstrap/assembly"
JAR=$(find target/out -name "bootstrap-assembly-*.jar" | sort | tail -1)
java -cp "$JAR" dev.cmartin.aerohex.bootstrap.Main
```

On startup the app runs Flyway migrations in-process, before the HTTP port binds — new
`V*.sql` files under `infrastructure/migration/` apply automatically, and a migration failure
aborts startup. Set `FLYWAY_MIGRATE_ON_START=false` to skip.

> **One-time, per machine:** if your dev database predates migrate-on-start (hand-applied
> schema, no `flyway_schema_history` table), reset it once — `docker compose down -v &&
> docker compose up -d`, start the app (applies `V1`–`V7` from scratch), then re-seed:
> `docker exec -i aero-hex-ai-postgres-1 psql -U aviation -d aviation < plans/seed-data-countries-airports.sql`

Verify it's up:

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/docs/docs.yaml   # → 200
```

Swagger UI is served at `http://localhost:8080/docs`.

## API status

**Countries** (CRUD + search) and **Airports** (list, search, find, create, update,
list-by-country) are implemented end-to-end against real Postgres; Airlines, Aircraft,
Flights, Flight Instances, and Route creation are stubbed. See [CLAUDE.md](./CLAUDE.md#rest-api)
for the per-endpoint status table.

## Development

See [CLAUDE.md](./CLAUDE.md#build-commands) for build/test/coverage commands, versioning
policy, and architectural conventions.

Unit tests (`sbt "testOnly *"`) run against in-memory stubs / a Tapir stub server and never touch a
real database. A separate opt-in suite exercises the persistence layer against a real Postgres
started via Testcontainers (requires Docker):

```bash
sbt integrationTests/test
```

See [CLAUDE.md](./CLAUDE.md#integration-tests-opt-in-real-postgres) for coverage and setup details.

## TODO

- Create validation for domain using ZIO Prelude Validation, smart constructor, or validation layer.
