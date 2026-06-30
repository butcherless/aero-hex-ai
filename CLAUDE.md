# Aviation Hexagonal — CLAUDE.md

Scala 3 multi-module project demonstrating Hexagonal Architecture with ZIO.
Domain concepts: **Country → Airport → Airline → Route** with an outbox pattern for Kafka events.

## Git workflow

Always ask for confirmation before pushing. Never push automatically after a commit.

## Build commands

```bash
sbt compile           # compile all modules
sbt "testOnly *"      # run all tests (SBT 2.0: use testOnly *, not test)
sbt scalafmtAll       # format all sources (run before committing new files)
sbt scalafmtCheckAll  # check formatting (CI gate; requires git-tracked files)
sbt bloopInstall      # regenerate .bloop/ after dependency changes
sbt dependencyUpdates # show outdated dependencies
```

## After every implementation

```bash
sbt scalafmtAll  # format
sbt compile      # must pass with zero errors and zero warnings
```

Do not report the work as done until both succeed.

## Running the application

Start local infrastructure first:

```bash
docker compose up -d
```

Build and run as fat JAR (`java -jar` runs the spec generator, not the server):

```bash
pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true
sbt ";clean;bootstrap/assembly"
JAR=$(find target/out -name "bootstrap-assembly-*.jar" | sort | tail -1)
java -cp "$JAR" dev.cmartin.aerohex.bootstrap.Main
```

Verify: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/docs/docs.yaml` → `200`

## Versioning policy

- **Scala** — LTS only (3.3.x). Never upgrade to a non-LTS minor. The `scala3-library` 3.8.x entry in `dependencyUpdates` is the SBT meta-build; ignore it.
- **Direct deps** — stable GA only (no `-RC`, `-M`, `-SNAPSHOT`). Exception: Doobie 1.x has no stable release yet; stay on the latest RC.
- **Transitive deps** — let SBT resolve via eviction; only force an override for a known vulnerability or binary-incompatibility.
- **Updates** — run `dependencyUpdates` before each feature cycle. Patch/minor updates are free; major bumps need migration-guide review and passing compile + tests.

## Tech stack

| Concern | Library | Version |
|---|---|---|
| Language | Scala 3 LTS | 3.3.8 |
| Build | SBT | 2.0.1 |
| Effect | ZIO | 2.1.26 |
| HTTP server | ZIO HTTP | 3.11.3 |
| HTTP endpoints | Tapir | 1.13.25 |
| Persistence | Doobie + zio-interop-cats | 1.0.0-RC9 / 23.1.0.13 |
| Persistence (POC) | Quill | 4.8.6 |
| Messaging | ZIO Kafka | 3.6.0 |
| Migrations | Flyway | 12.9.0 |
| Database | PostgreSQL JDBC | 42.7.12 |
| JSON | Circe | 0.14.16 |
| Logging | ZIO Logging + SLF4J + Logback | 2.5.3 / 1.5.37 |

## Module dependency graph

```
shared-kernel
    └── domain
            ├── application
            ├── persistence-postgres   (infrastructure)
            ├── persistence-quill      (infrastructure — Quill POC)
            ├── messaging-kafka        (infrastructure)
            └── adapter-http
                        └── bootstrap  (composition root)
                migration              (standalone — SQL + Flyway only)
```

Rule: inner modules never depend on outer ones. `domain` has zero framework dependencies.

## Hexagonal layer conventions

- **`domain/`** — pure logic, no I/O, no framework imports. Opaque types for identifiers. Ports are plain Scala traits.
  - `model/` — Country, Airport, Airline, Route, OutboxEvent
  - `error/DomainError.scala` — sealed error hierarchy
  - `service/` — pure domain services (RouteValidator)
  - `port/in/` — driving ports / use-case interfaces
  - `port/out/` — driven ports / repository + publisher interfaces
- **`application/`** — orchestrates ports, implements `port/in`. Each service has a companion `ZLayer`.
- **`persistence-postgres/`** — Doobie implementations of `port/out` repositories.
- **`persistence-quill/`** — Quill POC implementation of `CountryRepository`. Not wired into bootstrap.
- **`messaging-kafka/`** — ZIO Kafka producer and outbox relay.
- **`migration/`** — Flyway SQL migrations; no domain dependency.
- **`adapter-http/`** — Tapir endpoint definitions + ZIO HTTP server. DTOs live here; `ErrorMapper` maps `DomainError` → HTTP status.
- **`bootstrap/`** — sole composition root. `WiringModule` wires all `ZLayer`s. `Main` runs migration then starts HTTP server + outbox relay concurrently.
- **`shared-kernel/`** — cross-cutting value types (`Pagination`, `NonEmptyString`).

## Key patterns

**Opaque types** — use `.value` to unwrap:
```scala
IataCode("MAD")       // construct
airport.iata.value    // unwrap to String
```

**ZLayer wiring** — every infrastructure class exposes a companion `val layer`:
```scala
object DoobieAirportRepository:
  val layer: URLayer[Transactor[Task], AirportRepository] =
    ZLayer.fromFunction(new DoobieAirportRepository(_))
```

**`UIO` for infallible queries** — `findAll`/`searchByName` return `UIO[List[A]]`; no `.mapError` needed in routes.

**Tapir endpoints** — each endpoint is converted individually via `.zServerLogic` to avoid union-type inference issues:
```scala
ZioHttpInterpreter().toHttp(myEndpoint.zServerLogic(...))  // one at a time
```
`toHttp` returns `Routes[Any, Response]`; seal with `.handleError(identity)` before `Server.serve`.

**Outbox pattern** — `CreateRouteService` writes to `outbox_events`. `OutboxRelay` polls every 5 s, publishes to Kafka, and marks events published.

**`ZIOAspect` vs `@@`** — `@@` widens the error type to `Any`; call `.apply()` directly on the aspect to preserve `UIO` / specific error types.

## Pending implementations (`???`)

These compile but throw `NotImplementedError` at runtime:

| File | Missing |
|---|---|
| `PostgresConfig.transactorLayer` | `HikariTransactor` as a ZIO scoped resource |
| `RouteEventCodec.routeCreatedSerde` | ZIO Kafka 3.x `Serde` with Circe JSON |
| `RouteEventProducer.publish` | `Producer.produce` call |
| `WiringModule.appLayer` | wires through the `???` transactor |

## Database schema

Flyway migrations in `infrastructure/migration/src/main/resources/db/migration/`:

```
V1 — countries     (PK: code VARCHAR(2))
V2 — airports      (PK: iata_code VARCHAR(3), FK → countries)
V3 — airlines      (PK: icao_code VARCHAR(3), FK → countries)
V4 — routes        (PK: UUID, FK → airports × 2 + airlines; UNIQUE origin+dest+airline)
V5 — outbox_events (PK: UUID, JSONB payload, published BOOLEAN, partial index on unpublished)
```

## Local infrastructure

`docker-compose.yml`:
- **Postgres 16** on `localhost:5432` — database/user/password: `aviation`
- **Kafka** (KRaft, no ZooKeeper) on `localhost:9092` — auto-creates topics

Environment variables (with fallbacks):
```
POSTGRES_URL / POSTGRES_USER / POSTGRES_PASSWORD
KAFKA_BOOTSTRAP_SERVERS / KAFKA_GROUP_ID
HTTP_PORT  (default 8080)
```

## REST API

Swagger UI: `http://localhost:8080/docs`

| Resource | Method | Path | Status |
|---|---|---|---|
| Countries | GET | `/api/v1/countries` | ✓ implemented |
| Countries | POST | `/api/v1/countries` | ✓ implemented |
| Countries | GET | `/api/v1/countries/search` | ✓ implemented |
| Countries | GET | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | PUT | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | DELETE | `/api/v1/countries/{code}` | ✓ implemented |
| Airports | GET | `/api/v1/airports` | stub |
| Airports | GET | `/api/v1/airports/{iata}` | stub |
| Airlines | GET | `/api/v1/airlines` | stub |
| Airlines | GET | `/api/v1/airlines/{icao}` | stub |
| Aircraft | GET | `/api/v1/aircraft` | stub |
| Aircraft | GET | `/api/v1/aircraft/{registration}` | stub |
| Flights | GET | `/api/v1/flights` | stub |
| Flights | GET | `/api/v1/flights/{code}` | stub |
| Journeys | GET | `/api/v1/journeys` | stub |
| Journeys | GET | `/api/v1/journeys/{id}` | stub |
| Routes | POST | `/api/v1/routes` | stub |

## Coverage

`coverageEnabled := true` and `coverageDataDir := baseDirectory.value / ".coverage-data"` apply to every module. `coverageDataDir` is outside `target/` so `sbt clean` never deletes the statement catalog.

**Per-module report:**
```bash
sbt "adapterHttp/testOnly *"
sbt adapterHttp/coverageReport
```

**Aggregate report:**
```bash
sbt compile
sbt "adapterHttp/testOnly *"   # repeat per module with tests
sbt coverageAggregate
# → target/out/jvm/scala-3.3.8/aviation-hexagonal/scoverage-report/index.html
```

**CAS caveat:** when `sbt compile` hits the content-addressed cache, it does not write local `.coverage-data/` dirs. The Scala 3 coverage `Invoker` then throws `ExceptionInInitializerError` at test runtime. Fix: `mkdir -p <module>/.coverage-data/scoverage-data` for every module before running `testOnly`. The CI workflow does this explicitly.

## Formatter

`.scalafmt.conf`: `maxColumn = 120`, `align.preset = most`, `newlines.source = keep`, `lineEndings = preserve`, `rewrite.scala3.removeOptionalBraces = false`, `project.git = true` (only git-tracked files formatted; run `sbt scalafmtAll` for new files).

## Documentation sources

Always fetch current docs before writing or modifying library API calls — training data may be stale, especially for ZIO Kafka 3.x and Doobie 1.x (both have breaking changes from prior versions).

1. **Context7 MCP** (`mcp__context7` tools) — preferred for quick lookups
2. **Official sites** (WebFetch fallback):
   - ZIO: https://zio.dev/reference/
   - ZIO HTTP: https://zio.dev/zio-http/
   - ZIO Kafka: https://zio.dev/zio-kafka/
   - Tapir: https://tapir.softwaremill.com/en/latest/
   - Doobie: https://tpolecat.github.io/doobie/
   - Flyway: https://documentation.red-gate.com/fd/
