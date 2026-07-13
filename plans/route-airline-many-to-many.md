# Route domain model fix: drop surrogate id, model Airline as many-to-many

## Goal

Reviewing `Route` (continuing the entity-hardening pass tracked in
`plans/entity-review-progress.md`) surfaced two modeling defects:

1. `Route.id: RouteId` was a DB-generated UUID surrogate key leaking into the domain model.
   Every other hardened entity (`Country`, `Airport`, `Airline`, `Aircraft`) keeps its surrogate
   id persistence-only and exposes only its natural key domain-wide — Route never got this
   treatment.
2. `Route.airlineIcao: IcaoCode` modeled a route as belonging to exactly one airline (DB
   `UNIQUE(origin, destination, airline)`). Real-world routes are many-to-many with airlines — a
   route is flown by several airlines, and an airline operates many routes. This was already
   flagged as an open question in `docs/analysis/01-domain-model.md` §7 (`Flight.airlineIcao` vs
   `Route.airlineIcao` redundancy) — `Flight` already carries its own `airlineIcao` alongside a
   route reference, so `Route`'s field was redundant, not just wrong cardinality.

## Decision

Build a real `route_airlines` join table (not an embedded airline list on `Route`), with
separate use-cases/endpoints to associate/disassociate. Route creation no longer touches
airlines at all. Surrogates stay database-only; the relationship is expressed purely through
repository finder methods on both sides — mirrors the existing
`AirportRepository.findByCountry`/`AirlineRepository.findByCountry` pattern already used for
Country↔Airport/Airline, rather than embedding relationship lists in either aggregate.

Rejected alternative: `CreateRoute` accepting an initial airline list at creation time — adds
complexity to the create path for no real benefit over a separate associate call.

Out of scope (deferred, not requested): wiring real Quill/Doobie persistence for Route
(`RouteRepository`/`RouteAirlineRepository` stay in-memory stubs in `WiringModule`, matching
Route's and Flight's pre-existing unwired state) and full Route CRUD (update/delete) beyond
what's needed for the airline association.

## What changed

- **`domain/route/Route.scala`** — `RouteId` opaque type deleted entirely. `Route` is now
  `case class Route(origin: IataCode, destination: IataCode, distanceKm: Int)`. Natural key is
  the `(origin, destination)` pair.
- **`domain/route/RouteRepository.scala`** — `findById`/`delete` keyed by `RouteId` became
  `findBySegment`/`delete` keyed by `(origin, destination)`.
- **`domain/route/RouteAirlineRepository.scala`** (new port/out) — `associate`/`disassociate`/
  `findAirlines`/`findRoutes`, backed by the new join table.
- **New port/in traits**: `AssociateAirlineUseCase`, `DisassociateAirlineUseCase`,
  `FindAirlinesByRouteUseCase`, `FindRoutesByAirlineUseCase` — each backed by
  `RouteAirlineRepository`, mirroring `FindAirlinesByCountryUseCase`'s shape.
- **`domain/error/DomainError.scala`** — `RouteNotFound` now carries `(origin, destination)`
  instead of an id; added `RouteAirlineAlreadyExists`/`RouteAirlineNotFound` (409/404).
- **`domain/flight/Flight.scala`** — `routeId: RouteId` → `origin`/`destination: IataCode`
  (Flight has no persistence/tests yet, so this was a low-risk, self-contained edit). Kept
  `airlineIcao` — it's no longer redundant now that `Route` carries no airline of its own.
- **`CreateRouteService`** — drops the airline step entirely; adds a duplicate-segment check via
  `findBySegment` before `save` (this closes BR-10 in `docs/analysis/01-domain-model.md`, which
  was previously documented but unenforced).
- **New application services**: `AssociateAirlineService`, `DisassociateAirlineService`,
  `FindAirlinesByRouteService`, `FindRoutesByAirlineService` — thin logging wrappers over
  `RouteAirlineRepository`, mirroring `DeleteAirlineService`'s style (not-found/conflict handling
  lives in the repository implementation, not the service).
- **`DoobieRouteRepository`** — updated for schema-consistency only (still unwired): drops the
  airline join, segment-based find/delete, omits `id` on insert (DB now generates it). No Doobie
  implementation was added for `RouteAirlineRepository` — new port, not part of the "keep
  existing repos consistent" obligation; left for whenever Route persistence is actually wired.
- **`V13__route_airline_many_to_many.sql`** — drops `routes.airline_id`/its FK/index/the old
  3-column unique constraint; adds a 2-column `UNIQUE(origin_airport_id, destination_airport_id)`;
  sets `routes.id DEFAULT gen_random_uuid()` (PG16 has this built in) since the domain no longer
  generates it; creates `route_airlines(route_id, airline_id)` with a composite PK.
- **Adapter-http**: `RouteDto`/`CreateRouteRequest` drop `id`/`airlineIcao`. New endpoints:
  `POST`/`DELETE /api/v1/routes/{origin}/{destination}/airlines/{icao}` (associate/disassociate,
  in `RouteEndpoints`), `GET /api/v1/routes/{origin}/{destination}/airlines` (in `AirlineEndpoints`,
  since it returns `AirlineDto` — mirrors how `AirlineEndpoints.findByCountry` lives there even
  though its path is under `countries/`), `GET /api/v1/airlines/{icao}/routes` (in
  `RouteEndpoints`, same returned-type-owns-the-endpoint mirroring).
- **`WiringModule`** — updated in-memory `routeRepoLayer` stub to the new shape; added an
  in-memory `routeAirlineRepoLayer` stub (same no-op style as the pre-existing Route/Flight
  stubs); wired the four new use-case layers.
- **`RouteEventCodec.RouteCreatedEvent`** — dropped `routeId`/`airlineIcao` fields (dead code,
  no call sites — still a `???` stub serde).
- **Tests**: rewrote `RouteEndpointsSpec`/`FlightEndpointsSpec` fixtures; added
  `RouteServiceSpec` (closes the "no service spec" gap `entity-review-progress.md` flagged for
  Route) plus repository test-double stubs (`RouteRepositoryStub`, `RouteAirlineRepositoryStub`);
  extended `AirlineEndpointsSpec`/`ErrorMapperSpec` for the new endpoints/error cases.

## Why this design

- Matches the codebase's own established convention exactly: surrogate ids never appear in a
  domain model (`Country`/`Airport`/`Airline`/`Aircraft` precedent), and cross-aggregate
  relationships are expressed as repository finder methods, not embedded collections
  (`AirportRepository.findByCountry`/`findCountryByIata` precedent).
- Keeps the change scoped to what was actually reviewed — no speculative Route CRUD, no premature
  Quill/Doobie wiring for a brand-new port with zero test coverage.
- Fixes a real, previously-documented defect (BR-10 unenforced, `Flight`/`Route` airline
  redundancy) as a side effect of the primary fix, rather than as unrelated scope creep — both
  were already called out in `docs/analysis/01-domain-model.md` before this change.

## Verification

`sbt scalafmtAll` → `sbt compile` (zero errors/warnings) → `sbt test` (81 application + 174
adapter-http tests, all green) — done. Deferred: `sync-postman-collection` skill (endpoints
changed), `validate-openapi` skill.
