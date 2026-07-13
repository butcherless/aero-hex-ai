# Harden the Flight entity: real persistence, full CRUD, tests

## Goal

`plans/entity-review-progress.md` flagged Flight as "not yet reviewed": no scaladoc, in-memory-
stub persistence, find-only HTTP surface with no path-param validator, no service-level tests, no
Quill/Doobie repos, and — the biggest gap — no Flyway table at all. This mirrored exactly where
Aircraft stood before its 2026-07-11 hardening pass, which this work mirrors file-for-file.

The progress doc's Flight row also carried one stale claim: it still said "redundant `airlineIcao`
flagged, unresolved," but that had already been resolved as a side effect of the Route↔Airline
many-to-many fix (`docs/analysis/01-domain-model.md` §7 Open Question #3) — `Route` no longer
carries an airline of its own, so `Flight.airlineIcao` is not redundant with it.

## Decision

`FlightCode` becomes a real ZIO Prelude `Newtype`, but loose — non-blank + max-length (8 chars)
only, mirroring `Registration`/BR-15's precedent — not a strict alphanumeric pattern, since
real-world IATA flight designators vary enough across codeshares/charters that a regex risks
rejecting valid codes.

Since this pass gives Flight a real table, it's wired into `WiringModule` like Country/Airport/
Airline/Aircraft (per the project's "every wired repository uses the same implementation"
policy) — unlike Route, which stays an intentional stub. `FlightInstance` stays unreviewed —
confirmed independent of Flight (no code-level coupling today; `FlightInstance.flightCode` is a
domain value, not a DB FK, since neither `flight_instances` nor a matching FK exists yet).

## What changed

- **`domain/flight/Flight.scala`** — `FlightCode` migrated to `Newtype[String]` (assertion
  `^.{1,8}$`, mirrors `Registration` exactly); added scaladoc to `Flight`/`FlightCode`.
- **`domain/flight/FlightRepository.scala`** — added `update` (previously missing).
- **New port/in traits**: `CreateFlightUseCase`, `UpdateFlightUseCase`, `DeleteFlightUseCase`,
  mirroring `Create/Update/DeleteAircraftUseCase` exactly.
- **`domain/error/DomainError.scala`** — added `FlightAlreadyExists`, `InvalidFlightCode`; mapped
  in `ErrorMapper` (409/400).
- **Application**: `CreateFlightService` (pre-check `findByCode` → `FlightAlreadyExists` else
  `save`), `UpdateFlightService`/`DeleteFlightService` (thin wrappers), mirroring
  `application/aircraft/*Service.scala`. Fixed `FindFlightService.findByCode`'s
  `FlightCode(code)` call, which stopped compiling once `FlightCode` became a Newtype (runtime
  strings need `.unsafeMake`, not the literal-only `apply`).
- **`V14__create_flights.sql`** — new table, surrogate id from creation (mirrors
  `V11__create_aircraft.sql`): `code VARCHAR(8) UNIQUE NOT NULL`, `alias VARCHAR(8)`,
  `sched_departure`/`sched_arrival TIME`, three FKs (`origin_airport_id`/
  `destination_airport_id → airports.id`, `airline_id → airlines.id`).
- **New `QuillAirportIdResolver`** (`infrastructure/persistence-quill/.../airport/`) — mirrors
  `QuillAirlineIdResolver` exactly; Quill had no airport-FK resolver before this (Flight is its
  first Quill-wired entity with one).
- **`QuillFlightRepository`/`DoobieFlightRepository`** — mirror `QuillAircraftRepository`/
  `DoobieAircraftRepository`'s shape, extended to three FK resolutions (origin, destination,
  airline) instead of one, using the new `QuillAirportIdResolver` + existing
  `QuillAirlineIdResolver` (Quill) and the existing generic `DoobieIdResolver` (Doobie).
- **`WiringModule.scala`** — `flightRepoLayer` switched from an in-memory `ULayer` stub to
  `QuillDataSourceLayer.live >>> QuillFlightRepository.layer` (`TaskLayer`); added
  `flightUseCaseLayers` combining Find/Create/Update/Delete.
- **Adapter-http**: `FlightDto` gained `CreateFlightRequest`/`UpdateFlightRequest`;
  `FlightEndpoints` gained a `codeParam` validator (non-blank, ≤8 chars, no pattern — same
  rationale as Aircraft's `registrationParam`) plus `create`/`update`/`delete`; `FlightRoutes`
  wired to all four use-cases (5 routes total). `ApiSpec.allEndpoints` updated with the 3 new
  endpoints — this manually-maintained list was the actual gap the Postman sync caught during
  the Route work, so it was checked proactively this time.
- **Tests**: `FlightRepositoryStub`, `FlightServiceSpec` (mirrors `AircraftServiceSpec`),
  `FlightRepositoryContractSpec` (integration-tests, seeds Country → two Airports (origin +
  destination) → Airline → Flight — one level deeper than Aircraft's chain since Flight has
  three FKs; 11 cases covering both airport FKs and the airline FK independently),
  `QuillFlightRepositoryItSpec`/`DoobieFlightRepositoryItSpec`, extended `FlightEndpointsSpec`
  (create/update/delete + real-`FlightCode.make`-not-a-stub test) and `ErrorMapperSpec`.

## Bugs caught during verification

- **`FlywayMigrationItSpec` hardcoded the expected final migration version** (`"12"`) — already
  stale from the Route work's V13, now also needs V14. Fixed to `"14"`.
- **`FlightRepositoryContractSpec` reused the airport code `"BRU"` across two test cases** — since
  every test in an `ItSpec` suite shares one Testcontainers Postgres instance
  (`.provideLayerShared`), the second seed attempt failed with a real `AirportAlreadyExists`
  collision. Renamed the second occurrence to `OST`/`LGG` (still real Belgian airports, just not
  reused). A reminder that contract-spec test data must be unique **across the whole file**, not
  just within one test.

## Why this design

Matches Aircraft's proven hardening template as closely as possible rather than inventing a new
shape: same layering (domain → application → persistence → HTTP), same error-handling
conventions (pre-check-then-save for Create, thin wrappers for Update/Delete, FK resolution
before every write), same test structure (service spec + repository stub + contract spec shared
across Quill/Doobie + endpoint spec). The only structural difference is three FK resolutions
instead of one, handled by adding the missing `QuillAirportIdResolver` mixin rather than
duplicating airport-resolution logic inline.

## Verification

`sbt scalafmtAll` → `sbt compile` (zero errors/warnings) → `sbt test` (281 tests, all green) →
`sbt integrationTests/test` (106 tests, all green, including the new
`FlightRepositoryContractSpec` run against both Quill and Doobie) — done. Deferred: Postman E2E
lifecycle folder for Flight (not added in this pass), `sync-postman-collection` +
`validate-openapi` skills.
