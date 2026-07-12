# Plan: repackage from package-by-layer to package-by-feature (within each module)

## Goal

Today, every module's *internal* package structure is organized by technical layer
(`model/`, `port/in/`, `port/out/`, `service/`, `endpoint/`, `dto/`, `repository/`, ...),
so working on one entity (e.g. Airport) means visiting a different package in every
module. Repackage each module's internals so that a feature's code sits together —
`domain.airport`, `application.airport`, `adapter-http.airport`,
`persistence.quill.airport`, `persistence.postgres.airport` — while every existing
module and its dependency direction stays exactly as-is.

## Explicit non-goal

**The sbt module graph does not change.** `domain → application → adapter-http /
persistence-postgres / persistence-quill / messaging-kafka → bootstrap` stays
intact, including the "inner modules never depend on outer ones" rule in `CLAUDE.md`.
That module split *is* this project's hexagonal-architecture demonstration; collapsing
it into per-feature modules (domain+application+adapter+persistence bundled under one
`country/` module, etc.) would defeat that purpose and is out of scope here. This plan
only touches sub-packages *within* each module.

## Current state (baseline)

Layer-named subpackages per module today:

| Module | Layer subpackages |
|---|---|
| `domain` | `model/`, `port/in/`, `port/out/`, `error/`, `service/` |
| `application` | `service/`, `aspect/` |
| `adapter-http` | `endpoint/`, `dto/`, `error/`, `server/` |
| `infrastructure/persistence-quill` | `repository/`, `config/` |
| `infrastructure/persistence-postgres` | `repository/`, `config/`, `mapper/` (empty) |
| `infrastructure/messaging-kafka` | `producer/`, `relay/`, `serde/`, `config/` |

Features present across the domain: **Country, Airport, Airline, Aircraft, Route,
Flight** (Flight + FlightInstance are one feature pair), **Outbox** (cross-entity
infrastructure concern, not a domain entity, but has its own port/repository/relay
trio worth keeping together).

## Target layout per module

Each module gets one subpackage per feature, holding every file for that feature
regardless of what layer-role it used to signal by directory name. Genuinely
cross-cutting code (used by *every* feature, owned by none) stays in a small,
explicitly-named package rather than being forced into a feature — see Decision 1.

### `domain`

```
domain/
  country/    Country.scala, CreateCountryUseCase.scala, FindCountryUseCase.scala,
              UpdateCountryUseCase.scala, DeleteCountryUseCase.scala, CountryRepository.scala
  airport/    Airport.scala, Create/Find/FindAirportsByCountry/Update/DeleteAirportUseCase.scala,
              AirportRepository.scala
  airline/    Airline.scala, Create/Find/Update/DeleteAirlineUseCase.scala, AirlineRepository.scala
  aircraft/   Aircraft.scala, Create/Find/Update/DeleteAircraftUseCase.scala, AircraftRepository.scala
  route/      Route.scala, CreateRouteUseCase.scala, RouteRepository.scala, RouteValidator.scala
  flight/     Flight.scala, FlightInstance.scala, FindFlightUseCase.scala,
              FindFlightInstanceUseCase.scala, FlightRepository.scala, FlightInstanceRepository.scala
  outbox/     OutboxEvent.scala, OutboxRepository.scala, EventPublisher.scala
  error/      DomainError.scala   (unchanged — see Decision 1)
```

`FindAirportsByCountryUseCase` moves under `airport/`, not `country/`: it's an
airport-lookup filtered by a country code, same as `FindAirportUseCase`.

### `application`

```
application/
  country/, airport/, airline/, aircraft/   one *Service.scala per use case, as today
  route/     CreateRouteService.scala   (only Create exists — Route is still a stub)
  flight/    FindFlightService.scala, FindFlightInstanceService.scala
  aspect/    ServiceAspect.scala   (unchanged — see Decision 1)
```

### `adapter-http`

```
adapter-http/
  country/   CountryDto.scala, CountryEndpoints.scala, CountryRoutes.scala
  airport/, airline/, aircraft/   same three-file shape
  route/     RouteDto.scala, RouteEndpoints.scala, RouteRoutes.scala
  flight/    FlightDto.scala, FlightEndpoints.scala, FlightRoutes.scala,
             FlightInstanceDto.scala, FlightInstanceEndpoints.scala, FlightInstanceRoutes.scala
  common/    PaginationParams.scala   (new package — see Decision 1)
  error/     EndpointErrors.scala, ErrorMapper.scala   (unchanged)
  server/    HttpServer.scala   (unchanged)
```

### `infrastructure/persistence-quill`

```
persistence/quill/
  country/   QuillCountryRepository.scala, QuillCountryIdResolver.scala
  airport/   QuillAirportRepository.scala
  airline/   QuillAirlineRepository.scala, QuillAirlineIdResolver.scala
  aircraft/  QuillAircraftRepository.scala
  common/    QuillSqlState.scala   (new package — see Decision 1)
  config/    QuillDataSourceLayer.scala   (unchanged)
```

### `infrastructure/persistence-postgres`

```
persistence/postgres/
  country/, airport/, airline/, aircraft/   one Doobie*Repository.scala each
  route/     DoobieRouteRepository.scala
  outbox/    DoobieOutboxRepository.scala
  common/    DoobieIdResolver.scala   (new package — see Decision 1)
  config/    PostgresConfig.scala   (unchanged)
```

The empty `mapper/` package is deleted (it holds nothing today).

### `infrastructure/messaging-kafka` (small, optional — see Decision 4)

```
messaging/kafka/
  route/     RouteEventProducer.scala, RouteEventCodec.scala
  outbox/    OutboxRelay.scala
  config/    KafkaConfig.scala   (unchanged)
```

### Untouched modules

- `bootstrap` (`Main.scala`, `OpenApiGenerator.scala`, `WiringModule.scala`) — this
  **is** the composition root; it legitimately spans every feature, so there is no
  single feature package to move it into. Only its *imports* change (Decision 2).
- `shared-kernel` (`Pagination.scala`, `NonEmptyString.scala`) — cross-cutting kernel
  types by definition, already not organized by layer.
- `infrastructure/migration` — one file, no layering to remove.
- `infrastructure/integration-tests` — organized by *backend* (`it/postgres/`,
  `it/quill/`, `it/migration/`, `it/support/`), a different axis than layer-vs-feature.
  Left out of this plan; flagged as an optional follow-up in Decision 4.

### Tests

Mirror `src/main` exactly, same as today:

- `application/src/test/.../application/service/*ServiceSpec.scala` → one spec per
  feature package (`application/country/CountryServiceSpec.scala`, etc.).
  `ServiceAspectSpec.scala` stays in `aspect/`.
- `RepositoryStubs.scala` is one `private[service]` file holding stub builders for
  all four wired repositories (Country/Airport/Airline/Aircraft). Split it into
  `CountryRepositoryStub.scala`, `AirportRepositoryStub.scala`, etc., one per feature
  test package, each `private[<feature>]` — consistent with moving the rest of the
  test tree, and each spec only needs its own entity's stub anyway.
- `adapter-http/src/test/.../endpoint/*EndpointsSpec.scala` → one spec per feature
  package. `ErrorMapperSpec.scala` stays in `error/`.

## Decisions

**1. Cross-cutting code stays in a small explicitly-named package, not forced into a feature.**
`DomainError`, `ServiceAspect`, `PaginationParams`, `EndpointErrors`/`ErrorMapper`,
`HttpServer`, `QuillSqlState`, `DoobieIdResolver`, and both `config/` packages are
used by *every* feature and owned by none. Package-by-feature literature (vertical
slice architecture) universally carves out a `common`/`shared` package for exactly
this case — forcing e.g. `DomainError` into one arbitrary feature package would just
relocate the layer problem instead of solving it, and every other feature would
import "across" a feature boundary that isn't real. New `common/` packages are added
only in `adapter-http`, `persistence-quill`, and `persistence-postgres`, where a
loose file currently sits directly in a layer folder with no other cross-cutting
company; `error/`, `aspect/`, `config/`, `server/` already read as cross-cutting names
today and are kept rather than renamed to `common/` purely for churn's sake.

**2. `WiringModule.scala` imports change from a handful of wildcards to many.**
It currently does `import domain.model.*`, `import domain.port.out.*`, `import
application.service.*` — three imports covering all six features. After the
refactor those become one wildcard import per feature per module (roughly
`domain.country.*, domain.airport.*, ..., application.country.*, ...` — a dozen-plus
lines). This is the single largest mechanical cost of the refactor since
`WiringModule` has the highest fan-in of any file in the codebase; budget time for
it specifically and recompile after every module lands (see Migration order).

**3. Use `git mv`, not delete-and-recreate, for every file move.**
Preserves line-level git blame/history across the rename. Package `object`
declarations are one-line edits per file after the move.

**4. `messaging-kafka` and `integration-tests` are optional, called out separately.**
`messaging-kafka` is 4 files already close to feature-shaped (`producer/`+`serde/`
are both Route-only; `relay/` is Outbox-only) — worth doing for consistency but low
value given the size, and it's dead code not wired into `bootstrap`. Do it last, or
skip it, without blocking the rest. `integration-tests` is organized by *persistence
backend*, which is a deliberate, different, useful axis (it answers "does the Doobie
Country repo work against real Postgres" as a unit) — repackaging it by feature would
lose that grouping. Leaving it alone; flag if a subsequent conversation wants a
by-feature *and* by-backend nested layout (`it/quill/country/`, `it/postgres/country/`).

## Migration order

Bottom-up through the dependency graph, recompiling after each step so breakage is
caught at the module that caused it rather than surfacing later in `bootstrap`:

1. `domain` — rename `model/`, `port/in/`, `port/out/`, `service/` into the seven
   feature packages; leave `error/` alone. `sbt domain/compile`.
2. `application` — depends only on `domain`; same treatment. `sbt application/compile`.
3. `adapter-http`, `infrastructure/persistence-quill`,
   `infrastructure/persistence-postgres` (any order — none depend on each other),
   each depending only on `domain` (+ `shared-kernel`). Compile each independently.
4. `infrastructure/messaging-kafka` (optional, Decision 4).
5. `bootstrap` — update `WiringModule.scala`/`Main.scala`/`OpenApiGenerator.scala`
   imports last, once every producer package name is final. `sbt compile` (whole
   build) must be clean here.
6. Test trees, module by module, mirroring steps 1–3 (`domain` has no tests today,
   so this starts at `application`).

After each step: `sbt scalafmtAll`, `sbt compile`, `sbt "testOnly *"` — per
`CLAUDE.md`'s existing "after every implementation" rule, applied per-step rather
than once at the end, so a broken step is bisectable.

## Risks / things to double check

- **Scoverage catalog staleness.** Moving every file changes statement IDs; the
  CAS-cache-bust dance in `CLAUDE.md`'s Coverage section will be needed before the
  next coverage report, same as any other genuine recompile.
- **Scalafmt** only touches git-tracked files (`project.git = true`); run
  `git add` for moved files *before* `sbt scalafmtAll` in each step, or newly-moved
  files silently won't be reformatted.
- **OpenAPI/Postman generation** (`OpenApiGenerator`, `docs/api/collection.json`) is
  driven by endpoint *definitions*, not package names — expect no functional change,
  but regenerate and diff once `adapter-http` lands to confirm the spec is
  byte-identical modulo nothing.
- **No behavior change anywhere** — this is a pure rename/move. Any test failure
  after a step indicates a missed import or a typo in a package clause, not a real
  regression.

## Open question for discussion

Should this land as one large PR (clean, avoids a long-lived half-migrated state,
but a big diff to review) or one PR per migration-order step above (smaller diffs,
but `bootstrap` is broken between steps 1–4 landing and step 5 landing unless each
intermediate PR is merged same-day)? Recommend one PR per step, merged in sequence
within a single working session, given the compile-after-each-step discipline above
already requires treating them as separate units of work.
