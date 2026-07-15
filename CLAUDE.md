# Aviation Hexagonal — CLAUDE.md

Scala 3 multi-module project demonstrating Hexagonal Architecture with ZIO.
Domain concepts: **Country → Airport → Airline → Aircraft → Route → Flight**, plus
**FlightInstance** (model + stub endpoints only), with an outbox pattern for Kafka events.

## Git workflow

Always ask for confirmation before pushing. Never push automatically after a commit.

After a push completes, do not monitor GitHub Actions (no `gh run watch`/polling loop) unless the
user explicitly asks for CI status.

## Build commands

```bash
sbt compile           # compile all modules
sbt test              # run all tests
sbt scalafmtAll       # format all sources (run before committing new files)
sbt scalafmtCheckAll  # check formatting (CI gate; requires git-tracked files)
sbt bloopInstall      # regenerate .bloop/ after dependency changes
sbt xdup              # show outdated dependencies (alias for dependencyUpdates)
```

## After every implementation

Run `sbt scalafmtAll` then `sbt compile` (must pass with zero errors and zero warnings) — do not
report the work as done until both succeed.

## Running the application

See [README.md](./README.md#running-locally) for the standard steps. Before restarting, kill any
previous instance first: `pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true`

## Versioning policy

- **Scala** — LTS only (3.3.x). Never upgrade to a non-LTS minor. The `scala3-library` 3.8.x entry in `dependencyUpdates` is the SBT meta-build; ignore it.
- **JDK** — Java 21 LTS required, locally and in CI (`java-version: '21'` in `.github/workflows/*.yml`). Never Java 25 or other
  non-LTS versions — Java 25 silently breaks ZIO 2.1.26's test framework (tests report "Failed" with zero SBT test events,
  no pass/fail per test). ZIO is only certified for Java 17/21.
- **Direct deps** — stable GA by default. Named exceptions: Doobie 1.x and ZIO Prelude 1.x (neither
  has a GA release yet) — don't chase a newer RC/M/SNAPSHOT for either without a deliberate reason
  (a GA release or a needed capability).
- **Transitive deps** — let SBT resolve via eviction; only force an override for a known vulnerability or binary-incompatibility.
- **Updates** — run `sbt xdup` before each feature cycle. Patch/minor updates are free; major bumps need migration-guide review and passing compile + tests.

## Tech stack

| Concern | Library | Version |
|---|---|---|
| Runtime | Java LTS | 21 |
| Language | Scala 3 LTS | 3.3.8 |
| Build | SBT | 1.12.13 |
| Effect | ZIO | 2.1.26 |
| Smart constructors (`CountryCode`/`IataCode`/`AirportIcaoCode`/`AirlineIcaoCode`/`Registration`) | ZIO Prelude | 1.0.0-RC47 |
| HTTP server | ZIO HTTP | 3.11.3 |
| HTTP endpoints | Tapir | 1.13.26 |
| Persistence (wired default) | Quill | 4.8.6 |
| Persistence (unwired alternate) | Doobie + zio-interop-cats | 1.0.0-RC9 / 23.1.0.13 |
| Connection pooling | HikariCP | 7.1.0 |
| Messaging | ZIO Kafka | 3.7.0 |
| Migrations | Flyway | 12.11.0 |
| Database | PostgreSQL JDBC | 42.7.13 |
| JSON | Circe | 0.14.16 |
| Logging | ZIO Logging + SLF4J + Logback | 2.5.3 / 1.5.38 |
| Integration testing | Testcontainers | 1.21.3 |
| HTTP-adapter tests (test scope) | sttp-client4 + Tapir stub server | 4.0.26 |

## Module dependency graph

```
shared-kernel
    └── domain
            ├── application
            ├── persistence-postgres   (infrastructure — unwired; Doobie repos kept schema-consistent)
            ├── persistence-quill      (infrastructure — wired into bootstrap; Country + Airport + Airline + Aircraft + Flight)
            ├── messaging-kafka        (infrastructure — not wired into bootstrap)
            └── adapter-http
                        └── bootstrap  (composition root: domain + application + adapter-http + persistence-quill + persistence-postgres + migration)
                migration              (SQL + Flyway only; no domain dependency — wired into bootstrap for migrate-on-start)
                integration-tests      (standalone — opt-in, real-Postgres tests; NOT in root's aggregate)
```

Rule: inner modules never depend on outer ones. `domain` has zero framework dependencies.

## Hexagonal layer conventions

- **`domain/`** — pure logic, no I/O, no framework imports. Opaque types for identifiers. Ports are plain Scala traits.
  - `model/` — Country, Airport, Airline, Route, Aircraft, Flight, FlightInstance, OutboxEvent
  - `error/DomainError.scala` — sealed error hierarchy
  - `service/` — pure domain services (RouteValidator)
  - `port/in/` — driving ports / use-case interfaces
  - `port/out/` — driven ports / repository + publisher interfaces
- **`application/`** — orchestrates ports, implements `port/in`. Each service has a companion `ZLayer`.
- **`persistence-postgres/`** — Doobie implementations of `port/out` (`DoobieCountryRepository`, `DoobieAirportRepository`, `DoobieAirlineRepository`, `DoobieAircraftRepository`, `DoobieRouteRepository`, `DoobieFlightRepository`, `DoobieOutboxRepository`). Unwired but kept schema-consistent, in case Doobie is chosen again.
- **`persistence-quill/`** — Quill implementations of `CountryRepository`/`AirportRepository`/`AirlineRepository`/`AircraftRepository`/`FlightRepository`; all five wired via `WiringModule`, sharing one `QuillDataSourceLayer.live` `DataSource`. Route/RouteAirline/FlightInstance are still an in-memory stub.
- **Persistence policy:** all wired repositories must use the same implementation — switching is all-or-nothing across every entity, in one commit (see the header comment in `WiringModule.scala`).
- **`messaging-kafka/`** — ZIO Kafka producer and outbox relay. Not wired into bootstrap.
- **`migration/`** — Flyway SQL migrations; no domain dependency. `Main` runs `FlywayMigration.migrateFromEnv` at startup (see the `bootstrap` bullet).
- **`adapter-http/`** — Tapir endpoint definitions + ZIO HTTP server. DTOs live here; `ErrorMapper` maps `DomainError` → HTTP status.
- **`bootstrap/`** — sole composition root. `WiringModule` wires all `ZLayer`s. `Main` runs Flyway migrations in-process (skip with `FLYWAY_MIGRATE_ON_START=false`), then starts the HTTP server (`WiringModule.appLayer`) — no outbox relay.
- **`shared-kernel/`** — cross-cutting value types (`Pagination`, `NonEmptyString`).

## Integration tests (opt-in, real Postgres)

`infrastructure/integration-tests/` runs `FlywayMigration`, `DoobieXxxRepository`, and
`QuillXxxRepository` against a real Postgres started via Testcontainers — no in-memory stubs, no
Tapir stub server. It is deliberately **not** in `root`'s `.aggregate(...)`, so `sbt compile`,
`sbt test`, and `sbt coverageAggregate` never touch it; invoke it explicitly:

```bash
sbt integrationTests/test   # or: sbt integrationTest (alias)
```

Coverage so far: `FlywayMigrationItSpec` (migrations reach `V14`), Country (`DoobieCountryRepositoryItSpec`
+ `QuillCountryRepositoryItSpec`, incl. `validateCode` success/failure against the `country_codes`
master table), Airport (`DoobieAirportRepositoryItSpec` +
`QuillAirportRepositoryItSpec`), Airline (`DoobieAirlineRepositoryItSpec` +
`QuillAirlineRepositoryItSpec`), Aircraft (`DoobieAircraftRepositoryItSpec` +
`QuillAircraftRepositoryItSpec`, seeding a `Country` then an `Airline` first since `aircraft.airline_id`
FKs to `airlines.id`), Flight (`DoobieFlightRepositoryItSpec` + `QuillFlightRepositoryItSpec`, seeding a
`Country`, two `Airport`s (origin + destination), and an `Airline` first since `flights.origin_airport_id`/
`destination_airport_id`/`airline_id` FK to `airports.id`/`airlines.id`) — each seeding its own `Country`
row first since `airports.country_id`/`airlines.country_id` FK to `countries.id` — 106 tests total, all
green. Route is not implemented yet.
See `plans/add-persistence-integration-tests.md` for the full scope table and design rationale (why a
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
FlightInstanceId.generate    // construct (UUID-based)
instance.id.value            // unwrap to UUID
```
Remaining plain opaque types: `FlightInstanceId`, `OutboxEventId`, `NonEmptyString`
(shared-kernel) — none has a real smart constructor.

`CountryCode`, `IataCode`, `AirportIcaoCode`, `AirlineIcaoCode`, `Registration`, and `FlightCode`
are ZIO Prelude `Newtype[String]`s instead, each with a real, enforced `assertion` (see
`docs/analysis/validation-analysis-hexagonal.md` §2/§6 for why). Same
`.value` unwrap convention either way; construct via `CountryCode("ES")` /
`IataCode("MAD")` / `AirportIcaoCode("LEMD")` / `AirlineIcaoCode("IBE")` /
`Registration("EC-MIG")` / `FlightCode("UX9117")`
for compile-time-known literals (a malformed literal fails to compile),
`.make(raw).toZIO` for runtime strings that need a single fail-fast check,
`.validateAll(raw)` for runtime strings that should accumulate *every* failing
rule instead of stopping at the first (blank / length / shape are independent
`zio.prelude.Validation`s combined with `Validation.validateWith`, bridged to
`IO[DomainError, _]` via `.toEitherWith` + `ZIO.fromEither` — see
`FieldValidation` in `domain/validation/`), and `.unsafeMake(raw)` for
already-trusted data (DB reads, Tapir-already-validated path params,
cross-entity reference fields). Real validation is wired into each type's
*owning* entity's create path only (`CreateCountryRequest`/`CreateAirportRequest`/
`CreateAirlineRequest`/`CreateAircraftRequest`/`CreateFlightRequest`.toCommand,
via `.validateAll`) — a reference field on another entity (e.g.
`Aircraft.airlineIcao`, `Route.origin`, `Flight.origin`/`destination`/`airlineIcao`)
always uses `unsafeMake`, never `.make`/`.validateAll`. The HTTP error response
(`HttpErrorResponse`) carries both a short `message` and the full `errors: List[String]`
so a client sees every violated rule in one round trip, not just the first —
note that Tapir's own schema-level length `Validator`s (kept for OpenAPI-visible
bounds) already reject a wrong-length body before `toCommand` runs, so in
practice only the "letters only" rule is ever reachable through the live HTTP
endpoint for the four exact-length types; full multi-rule accumulation is
exercised directly against each type's `.validateAll` in `domain`'s test suite
(e.g. `CountryCodeSpec`).
`IataCode`'s assertion enforces both shape and its fixed 3-letter length. `AirportIcaoCode`
(4 letters, e.g. `"LEMD"`) and `AirlineIcaoCode` (3 letters, e.g. `"IBE"`) used to be one shared
`IcaoCode` type that could only enforce alphabetic shape, not length, since `Airline`'s own code
is 3 letters and `Airport`'s is 4; they're now two distinct types, each enforcing its own exact
length — closing that gap without needing any DB migration (`airlines.icao_code VARCHAR(3)` /
`airports.icao_code VARCHAR(4)` already matched). The HTTP-layer path-param/schema `Validator`s
for both were already correctly scoped to 3/4 letters and needed no change.
`Registration`'s assertion (non-blank, ≤10 chars) and `FlightCode`'s (non-blank,
≤8 chars) are both bound-for-bound identical to their HTTP `Validator`s, since
real-world registrations/flight designators have no fixed shape to check — reviewed and confirmed
still generous enough (real-world registrations/designators top out around 7-8 chars).

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
| `WiringModule.appLayer` | wires Quill `CountryRepository`, Quill `AirportRepository`, Quill `AirlineRepository`, Quill `AircraftRepository`, and in-memory stubs for everything else |

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
V8 — airports      adds `idx_airports_icao_code` — previously unindexed.
V9 — airlines      adds `foundation_date DATE NOT NULL` — the domain model's `Airline.foundationDate`
     always required this column, but no earlier migration created it; only latent because Airline
     was find-only at the time (no write path existed to insert a row missing the column).
V10 — airlines     adds `idx_airlines_name_trgm` — same ILIKE-search index pattern as V7 gave
      countries/airports, added even though no `searchByName` endpoint exists for Airline yet.
V11 — aircraft     new table (PK: surrogate `id BIGINT GENERATED ALWAYS AS IDENTITY`, natural key
      `registration VARCHAR(10)` UNIQUE NOT NULL, `type_code`/`description VARCHAR`, FK →
      `airlines.id`). Designed with a surrogate id from creation (unlike V1-V3, which needed V7 to
      retrofit one) since the surrogate-key convention was already established by the time this
      table was added.
V12 — country_codes new table (PK: `code VARCHAR(2)`, no other columns). A standalone master
      reference of all 249 current ISO 3166-1 alpha-2 codes, populated in the same migration.
      Deliberately **not** FK'd to `countries` — used only by `CountryRepository.isValidCode`
      to validate a new `Country`'s code on creation (`CreateCountryService`); `countries`
      itself still accepts whatever code a caller supplies at the schema level, same as before.
V13 — routes/route_airlines: drops `routes.airline_id`/its FK/index and the old 3-column
      `uq_route_segment`; replaces it with a 2-column `UNIQUE (origin_airport_id,
      destination_airport_id)` (Route is now airline-agnostic infrastructure). Sets
      `routes.id DEFAULT gen_random_uuid()` since the domain no longer generates it
      (`RouteId` was removed). Adds `route_airlines(route_id, airline_id)` — a new join table
      modeling Route↔Airline as many-to-many, composite PK, indexed on `airline_id` for the
      reverse lookup direction.
V14 — flights      new table (PK: surrogate `id BIGINT GENERATED ALWAYS AS IDENTITY`, natural key
      `code VARCHAR(8)` UNIQUE NOT NULL, `alias VARCHAR(8)`, `sched_departure`/`sched_arrival TIME`,
      FK → `origin_airport_id`/`destination_airport_id` (`airports.id`, ×2) and `airline_id`
      (`airlines.id`). Designed with a surrogate id from creation, like V11.
```

**Flyway runs at application startup**: `Main` executes `FlywayMigration.migrateFromEnv` before
the HTTP server binds (disable with `FLYWAY_MIGRATE_ON_START=false`; a failure aborts startup).
New migration files apply automatically on the next app start — no manual psql step. The dev
database is migration-produced (one-time reset performed 2026-07-09; see
`plans/run-flyway-on-startup.md` for the design and the adoption procedure for machines whose
database predates this).

## Local infrastructure

`docker-compose.yml`:
- **Postgres 18** on `localhost:5432` — database/user/password: `aviation`
- **Kafka** (KRaft, no ZooKeeper) on `localhost:9092` — auto-creates topics

Environment variables (with fallbacks):
```
POSTGRES_URL / POSTGRES_USER / POSTGRES_PASSWORD
KAFKA_BOOTSTRAP_SERVERS
HTTP_PORT  (default 8080)
FLYWAY_MIGRATE_ON_START  (default true; "false" skips the startup migration)
```
`bootstrap/src/main/resources/application.conf`'s `kafka.group-id` has no `${?KAFKA_GROUP_ID}`
override (unlike every other setting in that file) — setting the env var has no effect on the
running bootstrap app today. Harmless in practice since Kafka isn't wired into `Main` yet, but
worth fixing (add the override line) before Kafka wiring lands, to avoid a silent no-op.

## REST API

Code-first OpenAPI — Tapir endpoint definitions in Scala are the single source of truth, never a
hand-written spec file. Swagger UI: `http://localhost:8080/docs`. Full per-endpoint implementation
status table: [docs/api/endpoint-status.md](./docs/api/endpoint-status.md) — update it whenever an
endpoint moves from stub to implemented, or a new one is added.

## Plans directory

Non-trivial changes (new endpoints, schema/persistence migrations, test refactors) get a design doc
in `plans/` before implementation: goal, decisions with a recommendation + rejected alternatives,
steps, files touched. Keep docs after their work lands — they're the record of *why* — and update
one instead of duplicating it if a later change revises the same decision.

## Docs directory

`docs/` holds analysis and API artifacts (distinct from `plans/`, which holds implementation designs):

- `docs/analysis/01-domain-model.md` — DDD glossary + domain model with standard IATA/ICAO
  terminology; the source of truth for entity/value-object definitions.
- `docs/analysis/entity-relationship-draft.md` — working notes on entity relationships and
  cardinalities; a scratch space, **not** a source of truth — conclusions get promoted into
  `01-domain-model.md`.
- `docs/analysis-plan.md` — the task plan that drives the analysis docs (glossary → use cases → ADR).
- `docs/api/collection.json` + `environment.json` — Postman collection kept in sync with the
  Tapir-generated OpenAPI spec via the `sync-postman-collection` skill; regenerate after any
  endpoint change, never edit by hand. Its 5 `E2E — ...` folders are runnable against a live app
  via the `run-e2e-tests` skill — see `## Validation` below.
- `docs/api/endpoint-status.md` — per-endpoint implementation status table (see `## REST API`
  above); update whenever an endpoint's status changes.
- `docs/todo/` — analysis for future work not yet implemented (e.g. `auth-jwt.md`, JWT auth with
  Tapir + ZIO).

## Coverage

`coverageEnabled := true` and `coverageDataDir := baseDirectory.value / ".coverage-data"` apply to every module. `coverageDataDir` is outside `target/` so `sbt clean` never deletes the statement catalog.

**Per-module report:**
```bash
sbt adapterHttp/test
sbt adapterHttp/coverageReport
```

**Aggregate report:**
```bash
sbt compile
sbt adapterHttp/test   # repeat per module with tests
sbt coverageAggregate
# → target/scala-3.3.8/scoverage-report/index.html
```

**Missing-dir gotcha:** if a module's tests run without ever recompiling that module (nothing changed since the last compile), scoverage never writes its local `.coverage-data/` dir, and the instrumented run then fails with `ExceptionInInitializerError` at test runtime. Fix: `mkdir -p <module>/.coverage-data/scoverage-data` per module before running tests. Doesn't apply to CI's fresh checkout — every module gets compiled from scratch there (a dependency of `sbt test` itself), so the dir is always created; the workaround only matters locally, when reusing a warm build. Never `rm -rf` an existing `.coverage-data/` dir to "reset" it — `coverageDataDir` lives outside `target/` specifically so `sbt clean` never touches the statement catalog; deleting it yourself just recreates the same gap.

## Validation

Four independent layers, each catching a different class of problem — run the ones relevant to
what changed, not always all four:

1. **Local build** — `sbt clean` → `compile` → `test` (281 unit tests, in-memory stubs / Tapir
   stub server) → `integrationTests/test` (106 tests, real Postgres via Testcontainers, needs
   Docker — see `## Integration tests` above) → `bootstrap/assembly` (package) →
   `coverageAggregate` (see `## Coverage` above for the `mkdir -p .coverage-data/...` step first).
2. **OpenAPI spec** — `/validate-openapi` skill (`bash .claude/skills/validate-openapi/scripts/run.sh`).
   Rebuilds the jar from clean, generates the spec via `OpenApiGenerator`, then checks it with
   Redocly (OAS 3.1 schema conformance), an inline-schema-completeness check, and Spectral
   (best-practice lint). Reports an endpoint inventory table alongside pass/fail.
3. **End-to-end** — `/run-e2e-tests` skill
   (`bash .claude/skills/run-e2e-tests/scripts/run.sh`). Starts real Postgres and the real app,
   then runs the Postman collection's 5 dedicated `E2E — ...` folders (`docs/api/collection.json`)
   against it via Newman — the only workflow that drives the actual running server rather than
   stubs or a throwaway Testcontainers database. The skill doc has a load-bearing gotcha about
   which main class to launch; read it there rather than assuming `java -jar` works.
4. **CI** — no dedicated skill; check with `gh run list --workflow=scala.yml --limit 5` and
   `gh run view --log --job=<job-id>` after a push. The `build` job runs step 1's `compile`/`test`/
   `coverageAggregate`/`assembly` on every push/PR (never `integrationTests` here, deliberately
   excluded — see `## Integration tests`), so its coverage number is lower than a local run that
   includes the integration suite; that gap is expected, not a regression. A separate
   `integration-tests` job runs `sbt integrationTests/test` (real Postgres via Testcontainers,
   no `services:` container needed — ubuntu-24.04 runners already have Docker), but only when the
   workflow is manually dispatched with the `run_integration_tests` input checked — it never runs
   on a plain push/PR, matching `integrationTests`' own opt-in convention.

Layering, cheapest/narrowest first: unit tests (stubs) → integration tests (real Postgres,
opt-in module) → E2E (real server + real dev Postgres, closest to production) → OpenAPI
validation (contract correctness, an independent axis from runtime behavior) → CI (confirms
1–3 reproduce in GitHub's environment).

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
