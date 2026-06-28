# Aviation Hexagonal — CLAUDE.md

Scala 3 multi-module project demonstrating Hexagonal Architecture with ZIO.
Domain concepts: **Country → Airport → Airline → Route** with an outbox pattern for Kafka events.

## Build commands

```bash
sbt compile          # compile all modules
sbt test             # run all tests
sbt scalafmtAll      # format all sources (use before committing new untracked files)
sbt scalafmtCheckAll # check formatting (CI gate; requires files to be git-tracked)
sbt bloopInstall     # regenerate .bloop/ configs (after dependency changes)
sbt dependencyUpdates # show outdated dependencies (sbt-updates plugin)
```

## Coverage

The `coverage` command and `set` are incompatible with SBT 2.0's thin client (both trigger a build reload that fails). The only working approach is to set `coverageEnabled := true` directly in `build.sbt`, run the tasks as separate SBT calls, then restore.

**Two additional SBT 2.0 quirks to know before starting:**
- `test` is now incremental (`testQuick`) — after any `clean`, use `testOnly *` to run all tests.
- The CAS content-addressed cache does **not** recompile when `coverageEnabled` changes. You must modify a source file's content to bust the cache and trigger a real instrumented compile. Confirm recompilation by looking for `"compiling N Scala sources"` in the output. A `"Total time: 0s"` result means the cache was hit and no instrumentation was added.

**Workflow for `adapter-http` (adapt module name as needed):**

1. In `build.sbt`, uncomment `coverageEnabled := true` inside the target module's `.settings(...)` block.
2. Bust the CAS cache so the compiler runs with the scoverage plugin:
```bash
# Add and then remove a blank line from any .scala file in the module, e.g.:
echo "" >> adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/endpoint/CountryEndpoints.scala
```
3. Compile to regenerate `scoverage.coverage` (the statement catalog):
```bash
sbt adapterHttp/compile
# Must print "compiling N Scala sources" — if it says "Total time: 0s" the cache hit and step 2 is needed
```
4. Run tests and generate the report:
```bash
sbt "adapterHttp/testOnly *"
sbt adapterHttp/coverageReport
```
5. Revert the blank line added in step 2, remove `coverageEnabled := true` from `build.sbt`, and verify:
```bash
# Remove the blank line from the source file
sbt compile
```

HTML report: `target/out/jvm/scala-3.3.8/adapter-http/scoverage-report/index.html`
XML report:  `target/out/jvm/scala-3.3.8/adapter-http/scoverage-report/scoverage.xml`
Cobertura:   `target/out/jvm/scala-3.3.8/adapter-http/coverage-report/cobertura.xml`

**Aggregate report across all modules:**

Enable coverage in each module that has tests (repeat steps 1–4 above per module), then:
```bash
sbt coverageAggregate
```
Aggregate HTML: `target/out/jvm/scala-3.3.8/aviation-hexagonal/scoverage-report/index.html`

`coverageAggregate` collects every `scoverage-data/` directory it finds across sub-projects. Modules compiled with `coverageEnabled := true` but without tests will appear with 0 invocations and pull the aggregate statement rate down — this is expected.

## After every new implementation

After completing any new implementation or refactor, always run these two steps in order:

```bash
sbt scalafmtAll   # format all modified sources
sbt compile       # verify clean compilation with zero errors and zero warnings
```

Do not report the work as done until both commands succeed.

Start local infrastructure (Postgres + Kafka) before running the app:

```bash
docker compose up -d
sbt "bootstrap/run"
```

## Running the application

The app is always run as a fat JAR using `java -cp`. Never use `sbt "bootstrap/run"` for this — `java -jar` runs `OpenApiGenerator` (not the server).

**Stop any running instance (by process name):**
```bash
pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true
```

**Build and start:**
```bash
sbt ";clean;bootstrap/assembly"
JAR=$(find target/out -name "bootstrap-assembly-*.jar" | sort | tail -1)
java -cp "$JAR" dev.cmartin.aerohex.bootstrap.Main
```

**Verify the app is up:**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/docs/docs.yaml
# expect: 200
```

## Versioning policy

- **Scala** — always the current **LTS** release (3.3.x). Never upgrade to a non-LTS minor (e.g. 3.4, 3.5, 3.8). The `scala3-library` update shown by `sbt dependencyUpdates` pointing at 3.8.x is the SBT meta-build Scala; ignore it.
- **Direct dependencies** — only **stable GA releases** (no `-RC`, `-M`, `-SNAPSHOT`). Exception: if a library has never shipped a stable release (e.g. Doobie 1.x is still in RC), stay on the latest RC but document it and revisit when stable.
- **Transitive dependencies** — prefer to let SBT resolve them via eviction rather than forcing them explicitly. Add a forced version override only when there is a known vulnerability or a concrete binary-incompatibility. After any update, always run `sbt compile` and inspect eviction warnings.
- **Update cadence** — run `sbt dependencyUpdates` before starting a new feature cycle. Apply patch and minor updates freely; treat major-version bumps (e.g. Flyway 10 → 12, HikariCP 5 → 7) with extra review: check the migration guide and verify compile + test pass before merging.

## Tech stack

| Concern | Library | Version |
|---|---|---|
| Language | Scala 3 LTS | 3.3.8 |
| Build | SBT | 2.0.0 |
| Effect | ZIO | 2.1.26 |
| HTTP server | ZIO HTTP | 3.11.3 |
| HTTP endpoints | Tapir | 1.13.25 |
| Persistence | Doobie + zio-interop-cats | 1.0.0-RC9 (RC, no stable yet) / 23.1.0.13 |
| Messaging | ZIO Kafka | 3.6.0 |
| Migrations | Flyway | 12.9.0 |
| Database | PostgreSQL | 42.7.11 (JDBC) |
| JSON | Circe | 0.14.16 |
| Logging | ZIO Logging + SLF4J + Logback | 2.5.3 / 1.5.37 |

## Module dependency graph

```
shared-kernel
    └── domain
            ├── application
            ├── persistence-postgres   (infrastructure)
            ├── messaging-kafka        (infrastructure)
            └── adapter-http
                        └── bootstrap  (composition root)
                migration              (standalone — only SQL + Flyway runner)
```

Rule: inner modules never depend on outer ones. `domain` has zero framework dependencies.

## Hexagonal layer conventions

- **`domain/`** — pure business logic, no I/O, no framework imports. Models use opaque types. Ports are plain Scala traits.
  - `domain/model/` — entities (Country, Airport, Airline, Route, OutboxEvent)
  - `domain/error/DomainError.scala` — sealed error hierarchy
  - `domain/service/` — pure domain services (RouteValidator)
  - `domain/port/in/` — driving ports / use-case interfaces (FindAirportUseCase, CreateRouteUseCase, …)
  - `domain/port/out/` — driven ports / repository + publisher interfaces
- **`application/`** — orchestrates domain ports, implements `port/in` traits. Each service has a companion `ZLayer`.
- **`infrastructure/persistence-postgres/`** — Doobie implementations of `port/out` repositories.
- **`infrastructure/messaging-kafka/`** — ZIO Kafka producer and outbox relay.
- **`infrastructure/migration/`** — Flyway SQL migrations only; no domain dependency.
- **`adapter-http/`** — Tapir endpoint definitions + ZIO HTTP server. DTOs live here; `ErrorMapper` translates `DomainError` to HTTP status codes.
- **`bootstrap/`** — sole composition root. `WiringModule` wires all ZLayers. `Main` runs migration then starts the HTTP server + outbox relay concurrently.
- **`shared-kernel/`** — cross-cutting value types (`Pagination`, `NonEmptyString`).

## Key patterns

**Opaque types** wrap primitive identifiers — always use `.value` to unwrap:
```scala
IataCode("MAD")          // construct
airport.iata.value       // unwrap to String
```

**ZLayer wiring** — every infrastructure class exposes a companion `val layer: URLayer[Dep, Interface]`:
```scala
object DoobieAirportRepository:
  val layer: URLayer[Transactor[Task], AirportRepository] =
    ZLayer.fromFunction(new DoobieAirportRepository(_))
```

**Tapir endpoints** — defined as `PublicEndpoint` vals in companion objects, wired to ZIO logic via `.zServerLogic`. Each endpoint is converted individually (not as a list) to avoid union-type inference issues with `ZioHttpInterpreter().toHttp`:
```scala
ZioHttpInterpreter().toHttp(myEndpoint.zServerLogic(...))   // one at a time
```
`toHttp` returns `Routes[Any, Response]`; seal with `.handleError(identity)` before passing to `Server.serve`.

**Outbox pattern** — `CreateRouteService` writes events to the `outbox_events` table. `OutboxRelay` polls every 5 seconds, publishes to Kafka, and marks events as published.

## Pending implementations (`???`)

These compile but throw `NotImplementedError` at runtime:

| File | What's missing |
|---|---|
| `PostgresConfig.transactorLayer` | HikariTransactor as a ZIO scoped resource |
| `RouteEventCodec.routeCreatedSerde` | ZIO Kafka 3.x Serde with Circe JSON |
| `RouteEventProducer.publish` | actual Kafka `Producer.produce` call |
| `WiringModule.appLayer` | wires through `???` transactor — works structurally |

## Database schema

Five Flyway migrations in `infrastructure/migration/src/main/resources/db/migration/`:

```
V1 — countries     (PK: code VARCHAR(2))
V2 — airports      (PK: iata_code VARCHAR(3), FK → countries)
V3 — airlines      (PK: icao_code VARCHAR(3), FK → countries)
V4 — routes        (PK: UUID, FK → airports × 2, airlines; UNIQUE origin+dest+airline)
V5 — outbox_events (PK: UUID, JSONB payload, published BOOLEAN, partial index on unpublished)
```

## Local infrastructure

`docker-compose.yml` provides:
- **Postgres 16** on `localhost:5432`, database/user/password all `aviation`
- **Kafka** (KRaft mode, no ZooKeeper) on `localhost:9092`, auto-creates topics

Config is read from environment variables with fallbacks:
```
POSTGRES_URL / POSTGRES_USER / POSTGRES_PASSWORD
KAFKA_BOOTSTRAP_SERVERS / KAFKA_GROUP_ID
HTTP_PORT  (default 8080)
```

## REST API

Swagger UI available at `http://localhost:8080/docs` once the app is running.

| Method | Path | Use case |
|---|---|---|
| GET | `/api/v1/countries` | list countries (paginated) |
| GET | `/api/v1/countries/{code}` | find country by code |
| GET | `/api/v1/airports` | list airports (paginated) |
| GET | `/api/v1/airports/{iata}` | find airport by IATA code |
| POST | `/api/v1/routes` | create route |

## SBT plugins

All confirmed working with SBT 2.0 (in `project/plugins.sbt`):

| Plugin | Purpose |
|---|---|
| sbt-assembly 2.3.1 | fat JAR packaging |
| sbt-scalafmt 2.6.1 | code formatting |
| sbt-scoverage 2.4.4 | test coverage reports |
| sbt-bloop 2.1.0 | Bloop / BSP support |
| sbt-native-packager 1.11.7 | Docker / universal packaging |
| sbt-updates 0.7.0 | dependency update checks |

## IntelliJ import

Open the project root directory. IntelliJ detects `.bsp/sbt.json` (SBT 2 BSP, auto-generated) and offers BSP import — select it. Requires the Scala plugin.
Bloop configs in `.bloop/` can also be used if you prefer the Bloop BSP path.

## Documentation sources

When working on this project, always consult official library documentation before writing or modifying code that uses a library API. Prefer these sources in order:

1. **Official docs sites** (use WebFetch):
   - ZIO: https://zio.dev/reference/
   - ZIO HTTP: https://zio.dev/zio-http/
   - ZIO Kafka: https://zio.dev/zio-kafka/
   - Tapir: https://tapir.softwaremill.com/en/latest/
   - Doobie: https://tpolecat.github.io/doobie/
   - Flyway: https://documentation.red-gate.com/fd/
2. **context7** — use the `mcp__context7` tools if available in the session as a secondary source.

This is especially important for the pending `???` stubs — ZIO Kafka 3.x and Doobie 1.x both have breaking API changes from prior versions. Do not rely on training-data knowledge of these APIs; fetch current docs first.

## Formatter

`.scalafmt.conf` mirrors the team's `scala-3` project config:
- `maxColumn = 120`, `align.preset = most`
- `newlines.source = keep`, `lineEndings = preserve`
- `rewrite.scala3.removeOptionalBraces = false` — braces are NOT auto-removed
- `project.git = true` — only git-tracked files are checked; run `sbt scalafmtAll` for new files
