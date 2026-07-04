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

See [README.md](./README.md#running-locally) for the standard steps. Before restarting, kill any
previous instance first: `pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true`

## Versioning policy

- **Scala** — LTS only (3.3.x). Never upgrade to a non-LTS minor. The `scala3-library` 3.8.x entry in `dependencyUpdates` is the SBT meta-build; ignore it.
- **JDK** — Java 21 LTS required, locally and in CI (`java-version: '21'` in `.github/workflows/*.yml`). Never Java 25 or other
  non-LTS versions — Java 25 silently breaks ZIO 2.1.26's test framework (tests report "Failed" with zero SBT test events,
  no pass/fail per test). ZIO is only certified for Java 17/21.
- **Direct deps** — stable GA by default. Only named exception: Doobie 1.x (no GA release yet) —
  don't chase a newer RC/M/SNAPSHOT for it without a deliberate reason (a GA release or a needed capability).
- **Transitive deps** — let SBT resolve via eviction; only force an override for a known vulnerability or binary-incompatibility.
- **Updates** — run `dependencyUpdates` before each feature cycle. Patch/minor updates are free; major bumps need migration-guide review and passing compile + tests.

## Tech stack

| Concern | Library | Version |
|---|---|---|
| Runtime | Java LTS | 21 |
| Language | Scala 3 LTS | 3.3.8 |
| Build | SBT | 2.0.1 |
| Effect | ZIO | 2.1.26 |
| HTTP server | ZIO HTTP | 3.11.3 |
| HTTP endpoints | Tapir | 1.13.25 |
| Persistence (wired default) | Quill | 4.8.6 |
| Persistence (unwired alternate) | Doobie + zio-interop-cats | 1.0.0-RC9 / 23.1.0.13 |
| Messaging | ZIO Kafka | 3.6.0 |
| Migrations | Flyway | 12.10.0 |
| Database | PostgreSQL JDBC | 42.7.12 |
| JSON | Circe | 0.14.16 |
| Logging | ZIO Logging + SLF4J + Logback | 2.5.3 / 1.5.37 |
| Integration testing | Testcontainers | 1.21.3 |

## Module dependency graph

```
shared-kernel
    └── domain
            ├── application
            ├── persistence-postgres   (infrastructure — unwired; Doobie repos kept schema-consistent)
            ├── persistence-quill      (infrastructure — wired into bootstrap; Country + Airport)
            ├── messaging-kafka        (infrastructure — not wired into bootstrap)
            └── adapter-http
                        └── bootstrap  (composition root: domain + application + adapter-http + persistence-quill + persistence-postgres)
                migration              (standalone — SQL + Flyway only; not wired into bootstrap)
                integration-tests      (standalone — opt-in, real-Postgres tests; NOT in root's aggregate)
```

Rule: inner modules never depend on outer ones. `domain` has zero framework dependencies.

## Hexagonal layer conventions

- **`domain/`** — pure logic, no I/O, no framework imports. Opaque types for identifiers. Ports are plain Scala traits.
  - `model/` — Country, Airport, Airline, Route, Aircraft, Flight, Journey, OutboxEvent
  - `error/DomainError.scala` — sealed error hierarchy
  - `service/` — pure domain services (RouteValidator)
  - `port/in/` — driving ports / use-case interfaces
  - `port/out/` — driven ports / repository + publisher interfaces
- **`application/`** — orchestrates ports, implements `port/in`. Each service has a companion `ZLayer`.
- **`persistence-postgres/`** — Doobie implementations of `port/out` (`DoobieCountryRepository`, `DoobieAirportRepository`, `DoobieAirlineRepository`, `DoobieRouteRepository`, `DoobieOutboxRepository`). Unwired but kept schema-consistent, in case Doobie is chosen again.
- **`persistence-quill/`** — Quill implementations of `CountryRepository`/`AirportRepository`; both wired via `WiringModule`, sharing one `QuillDataSourceLayer.live` `DataSource`. The only resources backed by real persistence — everything else is an in-memory stub.
- **Persistence policy:** all wired repositories must use the same implementation — switching is all-or-nothing across every entity, in one commit (see the header comment in `WiringModule.scala`).
- **`messaging-kafka/`** — ZIO Kafka producer and outbox relay. Not wired into bootstrap.
- **`migration/`** — Flyway SQL migrations; no domain dependency. Not invoked by `Main` yet — `FlywayMigration.layer` exists but is unreferenced.
- **`adapter-http/`** — Tapir endpoint definitions + ZIO HTTP server. DTOs live here; `ErrorMapper` maps `DomainError` → HTTP status.
- **`bootstrap/`** — sole composition root. `WiringModule` wires all `ZLayer`s. `Main` only starts the HTTP server (`WiringModule.appLayer`) — no migration step, no outbox relay.
- **`shared-kernel/`** — cross-cutting value types (`Pagination`, `NonEmptyString`).

## Integration tests (opt-in, real Postgres)

`infrastructure/integration-tests/` runs `FlywayMigration`, `DoobieXxxRepository`, and
`QuillXxxRepository` against a real Postgres started via Testcontainers — no in-memory stubs, no
Tapir stub server. It is deliberately **not** in `root`'s `.aggregate(...)`, so `sbt compile`,
`sbt "testOnly *"`, and `sbt coverageAggregate` never touch it; invoke it explicitly:

```bash
sbt integrationTests/test   # or: sbt integrationTest (alias)
```

Coverage so far: `FlywayMigrationItSpec` (migrations reach `V7`), Country (`DoobieCountryRepositoryItSpec`
+ `QuillCountryRepositoryItSpec`), Airport (`DoobieAirportRepositoryItSpec` +
`QuillAirportRepositoryItSpec`, each seeding its own `Country` row first since `airports.country_id`
FKs to `countries.id`) — 36 tests total, all green. Airline and Route are not implemented yet. See
`plans/add-persistence-integration-tests.md` for the full scope table and design rationale (why a
plain subproject instead of sbt's deprecated `IntegrationTest` config, why one module instead of
three, why fresh-container-per-suite).

Two gotchas baked into the setup, both documented with why in the plan doc:
- `build.sbt` sets `Test / javaOptions += "-Dapi.version=1.41"` because Testcontainers 1.21.x's
  Docker-environment probe falls back to a hardcoded, very old API version when none is negotiated,
  and recent Docker Desktop releases reject that below their `MinAPIVersion` — surfacing as a
  misleading "Could not find a valid Docker environment" with no obvious cause unless you add a
  temporary SLF4J binding to see the underlying 400 from the daemon.
- **Every spec must call `.provideLayerShared(...)`, never `.provideLayer(...)`,** on its
  `suite(...)`. `provideLayer` silently rebuilds the layer per `test` block instead of once per
  suite — caught during validation when 15 Country tests started 16 separate Postgres containers
  instead of 3.

## Key patterns

**Opaque types** — use `.value` to unwrap:
```scala
IataCode("MAD")           // construct
airport.iataCode.value    // unwrap to String
```

**ZLayer wiring** — every infrastructure class exposes a companion `val layer`:
```scala
object QuillAirportRepository:
  val layer: URLayer[DataSource, AirportRepository] =
    ZLayer.fromFunction(new QuillAirportRepository(_))
```

**`UIO` for infallible queries** — `findAll`/`searchByName` return `UIO[List[A]]`; no `.mapError` needed in routes.

**Tapir endpoints** — each `XxxRoutes` class converts its own endpoints via `.zServerLogic`, producing a `List[ZServerEndpoint[Any, Any]]`; all resources' lists are concatenated and passed to one `ZioHttpInterpreter().toHttp(...)` call together with the Swagger endpoints. `toHttp` returns `Routes[Any, Response]`; seal with `.handleError(identity)` before `Server.serve`.

**Outbox pattern (partial)** — `OutboxEvent`/`OutboxRepository`/`EventPublisher` ports and `OutboxRelay` (polls every 5 s, publishes to Kafka, marks events published) exist in `messaging-kafka`, but `CreateRouteService` does not yet write to `outbox_events`, and `OutboxRelay` is not wired into `Main`.

**`ZIOAspect` vs `@@`** — `@@` widens the error type to `Any`; call `.apply()` directly on the aspect to preserve `UIO` / specific error types.

## Pending implementations

| File | Status |
|---|---|
| `RouteEventCodec.routeCreatedSerde` | `???` — needs ZIO Kafka 3.x `Serde` with Circe JSON |
| `RouteEventProducer.publish` | compiles, but only logs the event — doesn't call `Producer.produce` |
| `WiringModule.appLayer` | wires Quill `CountryRepository`, Quill `AirportRepository`, and in-memory stubs for everything else |

## Database schema

Flyway migrations in `infrastructure/migration/src/main/resources/db/migration/`:

```
V1 — countries     (PK: code VARCHAR(2))
V2 — airports      (PK: iata_code VARCHAR(3), FK → countries)
V3 — airlines      (PK: icao_code VARCHAR(3), FK → countries)
V4 — routes        (PK: UUID, FK → airports × 2 + airlines; UNIQUE origin+dest+airline)
V5 — outbox_events (PK: UUID, JSONB payload, published BOOLEAN, partial index on unpublished)
V6 — airports      adds icao_code VARCHAR(4) NOT NULL — V2 never had this column even though
                    the Airport domain model/DTO/DoobieAirportRepository always required it;
                    caught only once persistence-postgres was actually wired into bootstrap.
V7 — countries/airports/airlines: PK → surrogate `id BIGINT GENERATED ALWAYS AS IDENTITY`; natural
     key (`code`/`iata_code`/`icao_code`) becomes a UNIQUE NOT NULL column for business lookups.
     Every FK now targets the parent's surrogate id instead of its natural key (`country_code` →
     `country_id`; `origin_iata`/`destination_iata`/`airline_icao` → `origin_airport_id`/
     `destination_airport_id`/`airline_id`). `routes.id`/`outbox_events.id` stay UUID (nothing FKs
     to them). Adds `pg_trgm` GIN indexes for the ILIKE `searchByName` finders (no index before).
     Surrogate id is persistence-only — domain/ports untouched. See
     `plans/surrogate-long-keys-country-airport.md`.
```

**Flyway is not actually invoked anywhere yet** (`FlywayMigration.layer` is unreferenced by `Main`) —
the local dev database's schema was applied by hand, and there is no `flyway_schema_history` table.
Until a migration step is wired in, apply new migration files manually against the running
Postgres container.

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

**Code-first OpenAPI.** Tapir endpoint definitions are the single source of truth — types,
validators, descriptions, and examples are declared in Scala. `OpenApiGenerator` (in
`bootstrap/`) calls Tapir's `OpenAPIDocsInterpreter` and writes the spec to stdout as YAML.
Running the fat JAR with `java -jar` executes the generator; `java -cp` runs the server.
Never maintain a hand-written spec file — always regenerate from code.

Swagger UI: `http://localhost:8080/docs`

| Resource | Method | Path | Status |
|---|---|---|---|
| Countries | GET | `/api/v1/countries` (optional `name` filter, ≥3 chars) | ✓ implemented |
| Countries | POST | `/api/v1/countries` | ✓ implemented |
| Countries | GET | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | PUT | `/api/v1/countries/{code}` | ✓ implemented |
| Countries | DELETE | `/api/v1/countries/{code}` | ✓ implemented |
| Airports | GET | `/api/v1/airports` | ✓ implemented |
| Airports | GET | `/api/v1/airports/search` (name filter, ≥3 chars) | ✓ implemented |
| Airports | GET | `/api/v1/airports/{iata}` | ✓ implemented |
| Airports | POST | `/api/v1/airports` | ✓ implemented |
| Airports | PUT | `/api/v1/airports/{iata}` | ✓ implemented |
| Airports | GET | `/api/v1/countries/{code}/airports` | ✓ implemented |
| Airlines | GET | `/api/v1/airlines` | stub |
| Airlines | GET | `/api/v1/airlines/{icao}` | stub |
| Aircraft | GET | `/api/v1/aircraft` | stub |
| Aircraft | GET | `/api/v1/aircraft/{registration}` | stub |
| Flights | GET | `/api/v1/flights` | stub |
| Flights | GET | `/api/v1/flights/{code}` | stub |
| Journeys | GET | `/api/v1/journeys` | stub |
| Journeys | GET | `/api/v1/journeys/{id}` | stub |
| Routes | POST | `/api/v1/routes` | stub |

## Plans directory

Non-trivial changes (new endpoints, schema/persistence migrations, test refactors) get a design doc
in `plans/` before implementation: goal, decisions with a recommendation + rejected alternatives,
steps, files touched. Keep docs after their work lands — they're the record of *why* — and update
one instead of duplicating it if a later change revises the same decision.

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
# → target/out/jvm/scala-3.3.8/aero-hex-ai/scoverage-report/index.html
```

**CAS caveat:** a cached `sbt compile` skips writing local `.coverage-data/` dirs, causing `ExceptionInInitializerError` at test runtime. Fix: `mkdir -p <module>/.coverage-data/scoverage-data` per module before `testOnly` (the CI workflow does this).

## Formatter

`.scalafmt.conf`: `maxColumn = 120`, `align.preset = most`, `newlines.source = keep`, `lineEndings = preserve`, `rewrite.scala3.removeOptionalBraces = no`, `project.git = true` (only git-tracked files formatted; run `sbt scalafmtAll` for new files).

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
