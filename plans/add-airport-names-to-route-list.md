# Plan: Return origin/destination airport names from `GET /api/v1/routes`

## Goal

The admin UI's Routes list only shows origin/destination IATA codes and distance — no
human-readable airport name, unlike Airports/Airlines lists elsewhere in the app. Extend the
route list/search endpoint (`RouteEndpoints.findAll`, backing `RoutesApi.list` in the frontend)
to also return each route's origin/destination airport `name`.

## Decision: widen the existing join, scoped to the list/search read path only

`QuillRouteRepository`'s `findBySegment`/`findAll`/`findByOrigin`/`findByDestination` already join
`airports` twice (origin + destination) via `QuillAirportIdResolver`'s `AirportRef(id, iataCode)`
projection, just to resolve IATA codes for filtering/sorting — `airports.name` is one extra column
in an already-present join, no new join needed.

Two things this stays deliberately narrow about:

- **`Route` (the domain model used by `save`/`update`/`findAllUnbounded`/`RouteAirlineRepository`)
  is untouched.** Airport names aren't part of a route's identity or persisted state — they're a
  read-time enrichment for exactly one use case (the list/search HTTP response). A new type,
  `RouteWithAirportNames(route: Route, originAirportName: String, destinationAirportName: String)`,
  carries the enrichment instead, mirroring the existing `AirportRepository.findAllUnboundedWithCountry:
  IO[DomainError, List[(Airport, CountryCode)]]` precedent (a tuple-shaped read model bolted onto a
  repository method that needs one, without touching the entity itself).
- **`AirportRef`/`QuillAirportIdResolver` (shared with `QuillFlightRepository` and
  `resolveAirportId`) is left alone.** A local `AirportNameRef(id, iataCode, name)` projection lives
  only inside `QuillRouteRepository`, so `QuillFlightRepository`'s joins and `resolveAirportId`'s
  `SELECT` don't pick up an unused `name` column.
- **`RouteDto`'s two new fields (`originAirportName`/`destinationAirportName`) are `Option[String]`,
  populated only by the list/search endpoint.** `RouteDto` is also the response type for `POST
  /api/v1/routes` (create) and `GET /api/v1/airlines/{icao}/routes` (`findByAirline`, backed by the
  still-in-memory-stub `RouteAirlineRepository` — see `CLAUDE.md`'s module graph) — neither of those
  paths has airport names available without a materially bigger change (enriching the create
  response, or teaching the in-memory route/airline stub about names), and neither is what the
  screenshot/request was about. `RouteDto.fromDomain(route: Route)` (used by both) leaves the two
  fields `None`; a new `RouteDto.fromDomainWithNames(RouteWithAirportNames)` (used only by
  `findAll`'s four branches) populates them.

Rejected: making `Route` itself carry the names (would force every `save`/`update`/create-flow
caller, and the `RouteAirlineRepository` stub, to fabricate or thread through name data they don't
have and don't need); widening the shared `AirportRef` (touches `QuillFlightRepository` for no
reason); a separate non-`RouteDto` response type for `findAll` (forces the frontend to maintain two
parallel Route DTOs for what's structurally the same resource).

## Scope check: which repository methods this actually touches

`RouteRepository.findBySegment`/`findAll`/`findByOrigin`/`findByDestination` are called from
exactly two places: `RouteRoutes.findAll`'s server logic (needs the names) and
`CreateRouteService.create` (`findBySegment`, existence check only — `.isDefined`, doesn't
destructure the route, so widening `Option[Route]` → `Option[RouteWithAirportNames]` is a
no-op there). `findAllUnbounded` is used by `master-data-sync`'s `RouteSync` for a plain
`Route`-keyed dedup set and stays `List[Route]`. `RouteAirlineRepository.findRoutes` (backing
`findByAirline`) is a separate port, untouched.

## Implementation

- `domain/.../route/Route.scala` — add `RouteWithAirportNames`.
- `domain/.../route/RouteRepository.scala`, `FindRouteUseCase.scala` — `findBySegment`/`findAll`/
  `findByOrigin`/`findByDestination` return `RouteWithAirportNames` (or `Option[...]`) instead of
  `Route`.
- `application/.../route/FindRouteService.scala` — signatures follow the port; bodies unchanged
  (pure delegation).
- `infrastructure/persistence-quill/.../route/QuillRouteRepository.scala` — local
  `AirportNameRef(id, iataCode, name)`, threaded through the four affected methods; `sortBy` tuple
  indices shift (destination `iataCode` moves from position 3 to 4 once `name` is inserted after
  each `iataCode`).
- `adapter-http/.../route/RouteDto.scala` — `originAirportName`/`destinationAirportName:
  Option[String]` + Tapir schema/description; `fromDomainWithNames`.
- `adapter-http/.../route/RouteRoutes.scala` — `findAll`'s four filter branches map through
  `fromDomainWithNames` instead of `fromDomain`.

## Tests

- `application/test/.../route/RouteRepositoryStub.scala` — stub signatures follow the port.
- `application/test/.../route/RouteServiceSpec.scala` — `FindRouteService` suite asserts against
  `RouteWithAirportNames`.
- `adapter-http/test/.../route/RouteEndpointsSpec.scala` — `defaultFindRoute`/`failingFindRoute`
  stubs and `GET /api/v1/routes` assertions updated.
- `infrastructure/integration-tests/.../support/RouteRepositoryContractSpec.scala` — real-Postgres
  contract tests assert against `.route` and the seeded airport names.

## Files touched

- `domain/src/main/scala/dev/cmartin/aerohex/domain/route/Route.scala`
- `domain/src/main/scala/dev/cmartin/aerohex/domain/route/RouteRepository.scala`
- `domain/src/main/scala/dev/cmartin/aerohex/domain/route/FindRouteUseCase.scala`
- `application/src/main/scala/dev/cmartin/aerohex/application/route/FindRouteService.scala`
- `infrastructure/persistence-quill/src/main/scala/dev/cmartin/aerohex/infrastructure/persistence/quill/route/QuillRouteRepository.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/route/RouteDto.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/route/RouteRoutes.scala`
- `application/src/test/scala/dev/cmartin/aerohex/application/route/RouteRepositoryStub.scala`
- `application/src/test/scala/dev/cmartin/aerohex/application/route/RouteServiceSpec.scala`
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/route/RouteEndpointsSpec.scala`
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/support/RouteRepositoryContractSpec.scala`
- `docs/api/endpoint-status.md`
- `docs/api/collection.json` (via `sync-postman-collection` skill)

## Follow-up (out of scope here)

The admin UI (`aero-ui-ai`, a separate repo) needs its own change to consume and render the two
new fields — deliberately left for a separate session, since that repo's `CLAUDE.md` declares
itself frontend-only and treats this backend repo as read-only reference.
