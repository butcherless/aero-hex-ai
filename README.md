# Aviation Hexagonal

[![Scala CI](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml/badge.svg)](https://github.com/butcherless/aero-hex-ai/actions/workflows/scala.yml)

A Scala 3 / ZIO multi-module application built from scratch to demonstrate **Hexagonal Architecture**
(ports & adapters). The domain models a small slice of the aviation world — **Country → Airport →
Airline → Route** — with a Kafka outbox pattern for domain events still being wired in.

## Tech stack

- **Scala 3** (LTS) + **SBT 2** multi-module build
- **ZIO** for functional effects, **ZIO HTTP** for the server, **Tapir** for endpoint definitions
  and code-first OpenAPI generation
- **Quill** over **PostgreSQL** persists `Country` (the only repository wired to a real database
  so far); Doobie + Flyway migrations exist in code but aren't wired into the running app yet
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

Verify it's up:

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/docs/docs.yaml   # → 200
```

Swagger UI is served at `http://localhost:8080/docs`.

## API status

**Countries** (CRUD + search) and **Airports** (list, search, find, create, update,
list-by-country) are implemented end-to-end against real Postgres; Airlines, Aircraft,
Flights, Journeys, and Route creation are stubbed. See [CLAUDE.md](./CLAUDE.md#rest-api)
for the per-endpoint status table.

## Development

See [CLAUDE.md](./CLAUDE.md#build-commands) for build/test/coverage commands, versioning
policy, and architectural conventions.
