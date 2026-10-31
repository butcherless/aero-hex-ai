# Aviation Hexagonal — CLAUDE.md

Scala 3 multi-module project demonstrating Hexagonal Architecture with ZIO.
Domain concepts: **Country → Airport → Airline → Aircraft → Route → Flight**, plus
**FlightInstance** (model + stub endpoints only), with an outbox pattern for Kafka events.

## Git workflow

Always ask for confirmation before pushing. Never push automatically after a commit.

After a push completes, do not monitor GitHub Actions (no `gh run watch`/polling loop) unless the
user explicitly asks for CI status.

## After every implementation

Run `sbt scalafmtAll` then `sbt compile` (must pass with zero errors and zero warnings) — do not
report the work as done until both succeed.

## Running the application

See [README.md](./README.md#running-locally) for the standard steps. Before restarting, kill any
previous instance first: `pkill -f "dev.cmartin.aerohex.bootstrap.Main" 2>/dev/null || true`

## Versioning policy

- **Scala** — always the latest LTS or newer, never a mainline "Next" release. Ignore the `scala3-library` line in `dependencyUpdates`; it's the SBT meta-build.
- **JDK** — Java 21 LTS, locally and in CI. Never Java 25 or other non-LTS versions: Java 25 has silently broken ZIO's test framework here (tests report "Failed" with zero SBT test events).
- **SBT** — stay on 1.x. A move to 2.x is a deliberate migration, reviewed like a major dependency bump.
- **Direct deps** — stable GA. Named exception: ZIO Prelude 1.x (no GA release yet); don't chase a newer RC/M/SNAPSHOT without a GA release or a needed capability.
- **Transitive deps** — let SBT resolve via eviction; only force an override for a known vulnerability or binary-incompatibility.
- **Updates** — run `sbt xdup` before each feature cycle. Patch/minor updates are free; major bumps need migration-guide review and passing compile + tests.

## Module dependency graph

```
shared-kernel
    └── domain
            ├── application
            ├── persistence-quill      (infrastructure — wired into bootstrap; Country + Airport + Airline + Aircraft + Flight + Route + User + RevokedToken)
            ├── messaging-kafka        (infrastructure — not wired into bootstrap)
            ├── security               (infrastructure — JWT issuance/validation + password hashing; TokenService/PasswordHasher port impls; wired into bootstrap; see plans/security/)
            └── adapter-http
                        └── bootstrap  (composition root: domain + application + adapter-http + persistence-quill + migration + security)
                migration              (SQL + Flyway only; no domain dependency — wired into bootstrap for migrate-on-start)
                integration-tests      (opt-in — domain + migration + persistence-quill, real-Postgres tests; NOT in root's aggregate)
                master-data-sync       (opt-in — domain + application + persistence-quill; downloads/parses/syncs Country + Airport + Airline + Route against real Postgres; NOT in root's aggregate; see plans/masterdata/)
```

Rule: inner modules never depend on outer ones. `domain` has zero framework dependencies.

## Hexagonal layer conventions

- **`domain/`** — pure logic, no I/O, no framework imports. Opaque types for identifiers. Ports are plain Scala traits.
  Organized by entity/feature, not by technical role — each package bundles its model, ports (both
  driving use-case interfaces and driven repository/publisher interfaces), and any pure domain
  service together:
  - `country/`, `airport/`, `airline/`, `aircraft/`, `route/`, `flight/` — one package per aviation
    entity, e.g. `route/` holds `Route`, `RouteRepository`, `CreateRouteUseCase`, and the pure
    domain service `RouteValidator`; `flight/` holds both `Flight` and `FlightInstance`
  - `outbox/` — `OutboxEvent`, `OutboxRepository`, `EventPublisher`
  - `user/` — `User` (authentication only, not part of the aviation business domain — see
    `plans/security/`), `UserRepository`, `TokenService`, `PasswordHasher`, `LoginUseCase`,
    `LogoutUseCase`, `RevokedTokenRepository`
  - `error/DomainError.scala` — sealed error hierarchy
  - `validation/FieldValidation.scala` — shared `zio.prelude.Validation` building blocks used by
    each entity's `Newtype.validateAll`
- **`application/`** — orchestrates ports, implements each entity's use-case interfaces (mirrors
  `domain/`'s per-entity package layout, e.g. `application/route/CreateRouteService.scala`). Each
  service has a companion `ZLayer`.
- **`persistence-quill/`** — Quill implementations of `CountryRepository`/`AirportRepository`/`AirlineRepository`/`AircraftRepository`/`FlightRepository`/`RouteRepository`/`UserRepository`/`RevokedTokenRepository`; all eight wired via `WiringModule`, sharing one `QuillDataSourceLayer.live` `DataSource`. RouteAirline/FlightInstance are still an in-memory stub.
- **Persistence policy:** all wired repositories must use the same implementation — switching is all-or-nothing across every entity, in one commit (see the header comment in `WiringModule.scala`).
- **`messaging-kafka/`** — ZIO Kafka producer and outbox relay. Not wired into bootstrap.
- **`security/`** — implements `TokenService` (`JwtService`, `jwt-scala`/`jwt-circe`, HS256, RFC
  7519 registered claims only) and `PasswordHasher` (`BcryptPasswordHasher`, `jbcrypt`). Depends
  only on `domain`, mirroring `persistence-quill`/`messaging-kafka`. See `plans/security/`.
- **`migration/`** — Flyway SQL migrations; no domain dependency. `Main` runs `FlywayMigration.migrateFromEnv` at startup (see the `bootstrap` bullet).
- **`adapter-http/`** — Tapir endpoint definitions + ZIO HTTP server. DTOs live here; `ErrorMapper` maps `DomainError` → HTTP status. Every business resource's endpoints require a valid bearer token (see `## Key patterns`'s Tapir endpoints entry and `## REST API`).
- **`bootstrap/`** — sole composition root. `WiringModule` wires all `ZLayer`s. `Main` runs Flyway migrations in-process (skip with `FLYWAY_MIGRATE_ON_START=false`), then starts the HTTP server (`WiringModule.appLayer`) — no outbox relay.
- **`shared-kernel/`** — cross-cutting value types (`Pagination`, `NonEmptyString`).

## Integration tests (opt-in, real Postgres)

Real-Postgres tests via Testcontainers, opt-in (`sbt integrationTests/test`), not in root's
aggregate. See [infrastructure/integration-tests/CLAUDE.md](./infrastructure/integration-tests/CLAUDE.md)
for scope, coverage, and its two setup gotchas.

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
`docs/analysis/validation-analysis-hexagonal.md` §2/§6 for why). Same `.value` unwrap convention
either way. Four ways to construct, by use case:
- A compile-time-known literal — `CountryCode("ES")` / `IataCode("MAD")` /
  `AirportIcaoCode("LEMD")` / `AirlineIcaoCode("IBE")` / `Registration("EC-MIG")` /
  `FlightCode("UX9117")` — a malformed literal fails to compile.
- `.make(raw).toZIO` — a runtime string that needs a single fail-fast check.
- `.validateAll(raw)` — a runtime string that should accumulate *every* failing rule instead of
  stopping at the first (blank / length / shape are independent `zio.prelude.Validation`s combined
  with `Validation.validateWith`, bridged to `IO[DomainError, _]` via `.toEitherWith` +
  `ZIO.fromEither` — see `FieldValidation` in `domain/validation/`).
- `.unsafeMake(raw)` — already-trusted data (DB reads, Tapir-already-validated path params,
  cross-entity reference fields).

`.validateAll` is wired only into each type's *owning* entity's create path (`Create<Entity>Request.toCommand`). A reference field on another entity (e.g. `Aircraft.airlineIcao`, `Route.origin`) always uses `unsafeMake`.

`HttpErrorResponse` carries a short `message` and the full `errors: List[String]`. Tapir's length `Validator`s (kept for OpenAPI bounds) reject wrong-length bodies before `toCommand` runs, so full multi-rule accumulation is only exercised against `.validateAll` in `domain`'s tests (e.g. `CountryCodeSpec`).

Shapes, matching the `VARCHAR` bounds and HTTP `Validator`s: `IataCode` 3 letters, `AirportIcaoCode` 4 letters, `AirlineIcaoCode` 3 letters, `Registration` non-blank ≤10 chars, `FlightCode` non-blank ≤8 chars.

**ZLayer wiring** — every infrastructure class exposes a companion `val layer`:
```scala
object QuillAirportRepository:
  val layer: URLayer[DataSource, AirportRepository] =
    ZLayer.fromFunction(new QuillAirportRepository(_))
```

**`UIO` for infallible queries** — `findAll`/`searchByName` return `UIO[List[A]]`; no `.mapError` needed in routes.

**Tapir endpoints** — every business resource's endpoints use Tapir's two-phase security: `.zServerSecurityLogic(secured)` (phase 1, `SecuredEndpoint.securityLogic` validating the bearer token) `.serverLogic { _ => ... }` (phase 2, the business logic) — see `plans/security/protect-endpoints.md`. Each `XxxRoutes` class produces a `List[ZServerEndpoint[Any, Any]]` this way; all resources' lists are concatenated and passed to one `ZioHttpInterpreter().toHttp(...)` call together with the Swagger endpoints (`/health/*` and login stay unprotected, plain `.zServerLogic`). `toHttp` returns `Routes[Any, Response]`; seal with `.handleError(identity)` before `Server.serve`.

**Outbox pattern (partial)** — `OutboxEvent`/`OutboxRepository`/`EventPublisher` ports and `OutboxRelay` (polls every 5 s, publishes to Kafka, marks events published) exist in `messaging-kafka`, but `CreateRouteService` does not yet write to `outbox_events`, and `OutboxRelay` is not wired into `Main`.

**`ZIOAspect` vs `@@`** — `@@` widens the error type to `Any`; call `.apply()` directly on the aspect to preserve `UIO` / specific error types.

## Pending implementations

| File | Status |
|---|---|
| `RouteEventCodec.routeCreatedSerde` | `???` — needs ZIO Kafka 3.x `Serde` with Circe JSON |
| `RouteEventProducer.publish` | compiles, but only logs the event — doesn't call `Producer.produce` |
| `WiringModule.appLayer` | wires Quill `CountryRepository`, `AirportRepository`, `AirlineRepository`, `AircraftRepository`, `FlightRepository`, `RouteRepository`, `UserRepository`, and `RevokedTokenRepository`, plus in-memory stubs for everything else (RouteAirline/FlightInstance) |
| `bootstrap/src/main/resources/application.conf`'s `kafka.group-id` | missing a `${?KAFKA_GROUP_ID}` override (every other setting in that file has one) — harmless today since Kafka isn't wired into `Main`, but a silent no-op once it is |

## Database schema

Flyway migrations in `infrastructure/migration/src/main/resources/db/migration/` are the source of truth; read them rather than expecting columns here. Worth knowing:

- Every FK targets the parent's surrogate `id BIGINT`, not its natural key. The surrogate id is persistence-only, so domain and ports never see it (`plans/surrogate-long-keys-country-airport.md`).
- The ISO country check is `CountryCode.isRecognized`/`validateIso`, a pure in-memory `Set` in `domain/country/IsoCountryCodes.scala`. V19 dropped the old `country_codes` table.
- `users` and `revoked_tokens` back JWT authentication, a separate bounded context (`docs/todo/auth-jwt.md`).

**Flyway runs at startup:** `Main` runs `FlywayMigration.migrateFromEnv` before the server binds (disable with `FLYWAY_MIGRATE_ON_START=false`; a failure aborts startup). New migrations apply on the next start with no manual step. For a machine whose database predates this, see `plans/run-flyway-on-startup.md`.

## Aircraft demo data

`Aircraft` has no sync module. [docs/aircraft-fleet-demo-data.md](./docs/aircraft-fleet-demo-data.md) has the manual OpenSky recipe.

## REST API

Code-first OpenAPI: Tapir definitions are the single source of truth, never a hand-written spec. Swagger UI: `http://localhost:8080/docs`. Every endpoint except `POST /api/v1/auth/login` and `/health/*` requires `Authorization: Bearer <token>`, and any valid token grants full access (no roles yet). **Update [docs/api/endpoint-status.md](./docs/api/endpoint-status.md) whenever an endpoint moves from stub to implemented or a new one is added.**

`/health/live` and `/health/ready` are deliberately unversioned, per the Kubernetes probe convention. Readiness pings Postgres with a 3s timeout, because HikariCP's own default is 30s.

## Plans directory

Non-trivial changes (new endpoints, schema/persistence migrations, test refactors) get a design doc in `plans/` before implementation: goal, decisions with a recommendation and rejected alternatives, steps, files touched. Keep docs after the work lands, and update one rather than duplicating it if a later change revises its decision. A multi-increment effort gets its own subdirectory with one doc per increment (e.g. `plans/masterdata/`, `plans/security/`).

## Docs directory

See [docs/CLAUDE.md](./docs/CLAUDE.md) for what's in each file.

## Coverage

`coverageEnabled` and `coverageDataDir` (`.coverage-data`) apply to every module. The data dir is outside `target/` so `sbt clean` never deletes the statement catalog. **Never `rm -rf` an existing `.coverage-data/`.**

```bash
sbt adapterHttp/test && sbt adapterHttp/coverageReport   # per module
sbt compile
sbt adapterHttp/test                                     # repeat per module with tests
sbt coverageAggregate                                    # → target/scala-<version>/scoverage-report/index.html
```

**Missing-dir gotcha (local only):** if a module's tests run without it recompiling, scoverage never writes its `.coverage-data/` dir, and the run fails with `ExceptionInInitializerError`. Create the dirs first:

```bash
for m in $(sbt -batch -no-colors printCoverageProjects | grep -v '^\['); do mkdir -p "$m/.coverage-data/scoverage-data"; done
```

## Validation

Independent layers, cheapest first. Run the ones relevant to what changed:

1. **Local build**: `sbt clean` → `compile` → `test` (stubs) → `integrationTests/test` (real Postgres, needs Docker) → `bootstrap/assembly` → `coverageAggregate` (see `## Coverage` for the `mkdir` step).
2. **End-to-end**: `/run-e2e-tests` starts real Postgres and the real app, then runs the Postman `E2E — ...` folders through Newman. It's the only layer driving the actual server. The skill doc explains which main class to launch; don't assume `java -jar` works.
3. **OpenAPI spec**: `/validate-openapi` rebuilds the jar, generates the spec, and checks it with Redocly, an inline-schema check and Spectral.
4. **CI**: check with `gh run list --workflow=scala.yml --limit 5`.
   - The `build` job runs compile, test, `coverageAggregate` and assembly on every push/PR, never `integrationTests`, so its coverage is lower than a local run that includes them. That's expected.
   - The `integration-tests` job only runs when manually dispatched with `run_integration_tests` checked.
   - Workflow design decisions are in `plans/refactor-github-actions-workflows.md`.

## Formatter

`.scalafmt.conf` sets `project.git = true`, so `git add` a new file before `sbt scalafmtAll` or it's silently skipped.

## Documentation sources

Always fetch current docs before writing or modifying library API calls, especially for ZIO Kafka 3.x (breaking changes).

**Choosing a library for a new capability:** check options in this order, against current docs rather than memory, and record the comparison (goal, options table, verdict):

1. ZIO core or an official ZIO-ecosystem library. This project prefers ecosystem fit over maturity (`zio-nio` over `better-files`/`os-lib`, `zio-http` over `sttp-client4`).
2. The Scala/JDK standard library, checking `scala.*` as well as `java.*`.
3. Other third-party libraries, only if neither of the above meets `## Versioning policy` or the required capability.

Once implemented, collapse the comparison to a one-line decision plus a bare list of rejected alternatives (see `docs/todo/master-data/analysis.md` §4.2–§4.5).

Where to look:

1. **Context7 MCP** (`mcp__context7` tools) — preferred for quick lookups
2. **Official sites** (WebFetch fallback):
   - ZIO: https://zio.dev/reference/
   - ZIO HTTP: https://zio.dev/zio-http/
   - ZIO Kafka: https://zio.dev/zio-kafka/
   - Tapir: https://tapir.softwaremill.com/en/latest/
   - Flyway: https://documentation.red-gate.com/fd/
