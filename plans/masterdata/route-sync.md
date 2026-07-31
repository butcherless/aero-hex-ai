# Implement Route sync: Airport coordinates, RouteCsvParser, RouteSync, real QuillRouteRepository

> **Status:** Implemented and verified live against a real local Postgres. Unit, master-data-sync,
> and integration tests all green.
>
> **Verified live** (starting from 4,532 existing airports at `latitude`/`longitude` `0`/`0` — the
> V20 migration default — and 0 routes): a first run picked up real coordinates for all 4,532
> airports (`updated: 4532, skippedConflict: 1` — the pre-existing Kosovo/`XK` nuance, unrelated to
> this change) but then crashed partway through the Airline step, before ever reaching Route — see
> "New nuance found during live verification" below. After that fix, a second run completed all
> four steps: Country `unchanged: 249`; Airport `unchanged: 4532, skippedConflict: 1`; Airline
> `unchanged: 1009, skippedInvalid: 24, skippedConflict: 1` (the same Austrian-Airlines-style
> aircraft-FK case, now caught instead of crashing); Route `created: 34782, skippedInvalid: 32870`
> (unknown-airport references plus per-operating-airline duplicates in `routes.dat` — expected, not
> a correctness problem). A third run reported Route `unchanged: 34782` (idempotent, no writes),
> matching every other entity's own idempotency guarantee. Spot-checked `MAD`'s shortest routes via
> `psql` — `MAD→RJL` 242 km, `MAD→VLC` 286 km, etc. — all sane real-world distances.

## Goal

Load `Route` data — including a real `distanceKm` — from OpenFlights' `routes.dat`, the same way
Country/Airport/Airline already sync from their own external sources. This reverses
`docs/todo/master-data/analysis.md` §1's earlier exclusion of Route from `master-data-sync`: that
exclusion was right for the *airline-to-route* association (`route_airlines` — still out of scope
here), but not for the route segment itself, which OpenFlights models as a genuine external
reference list.

Two things had to exist before Route could sync at all, since neither existed when this doc was
written:

1. **`RouteRepository` was a pure in-memory stub** in `bootstrap/WiringModule.scala` — `save`
   echoed its input without persisting, `findBySegment` always returned `None`. So before this
   work, nothing that wrote a `Route` (not the existing `POST /api/v1/route` endpoint, not a sync
   job) reached Postgres at all.
2. **`Airport` carried no coordinates** — neither the domain model nor the `airports` table had
   `latitude`/`longitude`, even though the source OurAirports CSV (already downloaded for the
   existing Airport sync) has them. Computing `distanceKm` via Haversine needs them from somewhere.

## Decisions

- **Persist `latitude`/`longitude` on `Airport`**, not compute them transiently inside the Route
  sync job. New `V20__add_airport_coordinates.sql` migration (`DOUBLE PRECISION NOT NULL DEFAULT
  0` on both columns — the default lets existing rows migrate cleanly; real values arrive by
  re-running the Airport sync once `AirportCsvParser` captures OurAirports' `latitude_deg`/
  `longitude_deg`). Chosen over a transient, sync-job-only lookup because it reuses the Airport
  sync's already-established CSV download/parse pipeline instead of a second, duplicate parse, and
  makes coordinates available to any future feature that needs them (e.g. a map), not just this one
  sync job. Also threaded through `CreateAirportCommand`/`UpdateAirportCommand` and
  `AirportDto`/`CreateAirportRequest`/`UpdateAirportRequest` (with a `Validator.min/max` range
  check, `-90..90`/`-180..180`) so the HTTP contract stays 1:1 with the domain model.
- **Pure `DistanceCalculator.haversineKm`** lives in `domain/route/` alongside `RouteValidator` —
  zero I/O, so it fits the domain layer's own convention for pure per-entity services, not
  master-data-sync (which only calls it, doesn't own the math).
- **`RouteRepository` needed a real Quill implementation** before any sync job could persist
  anything — built `QuillRouteRepository`, mirroring `QuillFlightRepository`'s exact
  `IataCode ↔ airport_id` translation pattern (resolve both airport ids up front via the existing
  `QuillAirportIdResolver` mixin for writes; a Quill `.join` back to `airports` twice for reads).
  No schema change needed — `routes` already had `origin_airport_id`/`destination_airport_id`
  (surrogate FKs) and `distance_km` since `V4`/`V7`/`V13`. Wired into `bootstrap/WiringModule.scala`
  in place of the stub — RouteAirline/FlightInstance remain stubs, untouched.
- **Route needed its own `Update`/`Delete`/`FindRouteUseCase` quartet** — until now Route only had
  `CreateRouteUseCase` (reads/writes elsewhere went straight through `RouteRepository`). Added to
  match the `Create`/`Update`/`Delete`/`Find` shape `EntitySync` depends on for every other synced
  entity, plus `RouteRepository.findAllUnbounded`/`.update`. `FindRouteUseCase`/
  `UpdateRouteUseCase`/`DeleteRouteUseCase` and their application-layer services
  (`FindRouteService`/`UpdateRouteService`/`DeleteRouteService`) are net-new; none is wired into any
  HTTP endpoint yet (out of scope for this step — `RouteRoutes` still exposes only
  create/associate/disassociate/findByAirline).
- **`routes.dat` has no header row** (like `airlines.dat`) — `RouteCsvParser` uses `scala-csv`'s
  `CSVReader.open(file.toFile).all()`, fixed positional columns (`2`=SourceAirport,
  `4`=DestinationAirport, `7`=Stops; Airline/AirlineID/DestinationAirportID/Codeshare/Equipment
  unused). Multi-leg rows (`Stops > 0`) are **silently filtered** at parse time — `Route` models
  direct segments only, the same "not relevant to this entity" tolerance
  `AirportCsvParser`'s type filter uses for non-large/medium airports. A blank source/destination
  column is logged+skipped (a genuine data problem, not a scope exclusion).
- **Airport resolution happens in `RouteSync`, not the parser**: `RouteCsvParser` only produces
  `RouteRow(sourceIata, destinationIata)`; `RouteSync.sync` loads every `Airport` up front (one
  `FindAirportUseCase.findAllUnbounded` call, mirroring every other sync's bulk-lookup pattern) into
  a `Map[IataCode, Airport]`, then resolves each row against it. A row whose origin or destination
  isn't in that map — or whose origin/destination resolve to the identical airport — is logged and
  skipped (`skippedInvalid`), never a hard failure.
- **Dedup by `(origin, destination)` before reconciling**: OpenFlights lists the same city pair
  once per operating airline (irrelevant here since `route_airlines` isn't populated), so
  `RouteSync` groups by the pair and keeps the first occurrence, logging/counting the rest —
  the same tolerance `AirlineSync` applies to duplicate ICAOs.
- **`EntitySync[K, E]`'s generics needed no changes** to support Route's composite key —
  `keyOf: Route => (IataCode, IataCode)` works exactly like `AirlineSync`'s
  `keyOf: (Airline, CountryCode) => AirlineIcaoCode`, just with a tuple `K` instead of a single
  `Newtype`.
- **`Main.scala`'s Route step can't reuse the generic `useCasesLayer` helper** other entities use —
  `CreateRouteService`/`UpdateRouteService` each need `FindAirportUseCase` in addition to
  `RouteRepository` (to resolve an IATA code to a real Airport), unlike Country/Airport/Airline's
  single-repo use-case quartet. Wired by hand instead: `routeUseCasesLayer` builds a
  `findAirportLayer` once and feeds it (alongside `routeRepoLayer`) into `Create`/`UpdateRouteService.layer`.
  Sequenced after Airline in `run` (routes depend on airports already being resolvable).
- **New nuance found during live verification, not anticipated in the design**: the first full
  `Main.run()` crashed partway through the *Airline* step — before ever reaching Route — hitting a
  pre-existing gap `docs/todo/master-data/analysis.md` §9 had already flagged as a real, not-yet-
  fixed risk: `QuillAirlineRepository.delete` only used `QuillSqlState.refineZeroRows`, which
  `.orDie`s any `SQLException` before checking the row count — so a foreign-key violation (an
  airline `AirlineSync` wants to delete because it's no longer in the current OpenFlights source,
  but which is still referenced by an `Aircraft` row) crashed the fiber instead of surfacing as a
  catchable `DomainError`, aborting the whole run. **Fixed**: added
  `DomainError.AirlineInUse(icao)`, a new `QuillSqlState.refineForeignKeyViolationOrZeroRows`
  combinator (catches Postgres SQLState `23503` — foreign-key violation — *before* the zero-rows
  check's own `.orDie`, mirroring `refineUniqueViolation`'s existing `23505` pattern), wired into
  `QuillAirlineRepository.delete`, plus an `ErrorMapper` case (409 Conflict) and a new integration
  test (`AirlineRepositoryContractSpec`, seeding an `Aircraft` referencing the airline before
  deleting it). `EntitySync.apply`'s existing per-row `foldZIO` catch already handles the rest —
  once the FK violation was a typed `DomainError`, it was automatically logged and counted as
  `skippedConflict` like any other row failure, no `EntitySync`/`AirlineSync` change needed. Scoped
  narrowly to `AirlineRepository.delete` only (what actually crashed) — the same latent risk likely
  exists on other entities' `delete` (`Country`/`Airport`/`Aircraft`/`Route` all have FK-referenced
  rows too) but fixing those is a separate, future concern, not part of this Route-sync change.

## Files touched

**New:**
- `infrastructure/migration/src/main/resources/db/migration/V20__add_airport_coordinates.sql`
- `domain/.../route/DistanceCalculator.scala` — pure Haversine.
- `domain/.../route/UpdateRouteUseCase.scala`, `DeleteRouteUseCase.scala`, `FindRouteUseCase.scala`
- `application/.../route/UpdateRouteService.scala`, `DeleteRouteService.scala`,
  `FindRouteService.scala`
- `infrastructure/persistence-quill/.../route/QuillRouteRepository.scala` — real implementation,
  mirroring `QuillFlightRepository`'s airport-id resolution/join pattern.
- `infrastructure/master-data-sync/.../RouteCsvParser.scala` — `RouteRow(sourceIata,
  destinationIata)`, `parse` (positional, no header, silent multi-leg filter).
- `infrastructure/master-data-sync/.../RouteSync.scala` — bulk airport lookup, dedup,
  `EntitySync.loadExisting/reconcile/apply`.
- `domain/src/test/.../route/DistanceCalculatorSpec.scala` — 2 tests (known distance ±5 km
  tolerance, identical-point → 0).
- `infrastructure/master-data-sync/src/test/.../RouteCsvParserSpec.scala` — 4 tests.
- `infrastructure/master-data-sync/src/test/.../RouteSyncSpec.scala` — 7 tests (create, update,
  idempotent unchanged, delete, unknown-airport skip, source dedup, multi-leg filter tolerance).
- `infrastructure/integration-tests/.../support/RouteRepositoryContractSpec.scala` — 9 tests
  (save/find/findAll/findAllUnbounded/update/delete + not-found/already-exists cases), real
  Postgres.
- `infrastructure/integration-tests/.../quill/QuillRouteRepositoryItSpec.scala`

**New (Airline delete fix, found during live verification):**
- Test case added to `infrastructure/integration-tests/.../support/AirlineRepositoryContractSpec.scala`
  ("delete fails with AirlineInUse when an aircraft still references the airline").

**Edited:**
- `domain/.../airport/Airport.scala`, `CreateAirportUseCase.scala`, `UpdateAirportUseCase.scala` —
  `latitude`/`longitude` fields.
- `domain/.../route/RouteRepository.scala` — `findAllUnbounded`, `update`.
- `infrastructure/persistence-quill/.../airport/QuillAirportRepository.scala` — column mapping.
- `infrastructure/master-data-sync/.../AirportCsvParser.scala` — captures
  `latitude_deg`/`longitude_deg`.
- `adapter-http/.../airport/AirportDto.scala` — `latitude`/`longitude` on
  `AirportDto`/`CreateAirportRequest`/`UpdateAirportRequest`, with a range `Validator`.
- `application/.../airport/CreateAirportService.scala`, `UpdateAirportService.scala` — pass through
  the two new fields.
- `bootstrap/.../WiringModule.scala` — `routeRepoLayer` now `QuillRouteRepository.layer` instead of
  the in-memory stub.
- `application/test/.../route/RouteRepositoryStub.scala` — `onFindAllUnbounded`/`onUpdate`.
- Test fallout from `Airport`'s new required fields (mechanical, no behavior change):
  `AirportServiceSpec`, `AirportEndpointsSpec`, `AirportCsvParserSpec`, `AirportSyncSpec`,
  `RouteServiceSpec`, `AirportRepositoryContractSpec`, `FlightRepositoryContractSpec`.
- `infrastructure/integration-tests/.../migration/FlywayMigrationItSpec.scala` — expects schema
  version `20`, not `19`.
- `infrastructure/master-data-sync/.../Main.scala` — `routeUrl` constant, hand-wired
  `routeUseCasesLayer`, a 4th sequential step (download → `RouteSync.sync` → log) after Airline.
- `domain/.../error/DomainError.scala` — new `AirlineInUse(icao)` case.
- `infrastructure/persistence-quill/.../common/QuillSqlState.scala` — new
  `refineForeignKeyViolationOrZeroRows` combinator.
- `infrastructure/persistence-quill/.../airline/QuillAirlineRepository.scala` — `delete` now uses
  the new combinator instead of `refineZeroRows`.
- `adapter-http/.../error/ErrorMapper.scala` — `AirlineInUse` → 409 Conflict.
- `infrastructure/integration-tests/.../quill/QuillAirlineRepositoryItSpec.scala` — provides
  `AircraftRepository` too, for the new FK-conflict test case.
- `docs/todo/master-data/analysis.md` — §1 scope (Route moves in-scope), status header.
- Root `CLAUDE.md` — module graph (`master-data-sync` now syncs Route too; persistence-quill's
  wired-repository list drops Route from the stub-only list).
- `infrastructure/integration-tests/CLAUDE.md` — coverage note, test count.

## Verification

1. `sbt scalafmtAll` then `sbt compile` — zero errors, zero warnings.
2. `sbt test` — 404 tests (was 398 + `DistanceCalculatorSpec`'s 2 + `Airport`'s new-field fallout
   with no new cases elsewhere), all green.
3. `sbt masterDataSync/test` — 61 tests (50 existing + 4 `RouteCsvParserSpec` + 7 `RouteSyncSpec`),
   all green.
4. `sbt integrationTests/test` — 74 tests (was 64 + 9 `RouteRepositoryContractSpec` + 1
   `AirlineInUse` case), all green against a real Testcontainers Postgres, including the
   `UNIQUE(origin_airport_id, destination_airport_id)` constraint and the
   `RouteNotFound`/`RouteAlreadyExists`/`AirlineInUse` mappings.
5. **Real end-to-end run against local Postgres** (`sbt masterDataSync/run`, dev DB already at
   4,532 airports / 0 routes): Country `unchanged: 249`; Airport `unchanged: 4532,
   skippedConflict: 1` (pre-existing Kosovo/`XK` nuance); Airline `unchanged: 1009,
   skippedInvalid: 24, skippedConflict: 1` (the Airline-in-use case, now caught cleanly); Route
   `created: 34782, skippedInvalid: 32870, skippedConflict: 0`. A second run reported Route
   `unchanged: 34782` (idempotent, no writes). Confirmed via `psql`: `routes` row count `0` →
   `34782`; spot-checked `MAD`'s shortest outbound routes (`MAD→RJL` 242 km, `MAD→VLC` 286 km,
   `MAD→PNA` 299 km, ...) — all real, sane distances.
