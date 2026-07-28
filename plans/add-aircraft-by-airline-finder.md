# Plan: `GET` — list aircraft belonging to an airline

## Goal

Add a finder that returns all aircraft operated by a given airline (e.g. "all Iberia
aircraft"), reachable at `GET /api/v1/airlines/{icao}/aircraft`. `Aircraft` currently
only exposes `findAll` (unfiltered, paginated) and `findByRegistration` — there is no
way to filter by `airlineIcao` short of fetching everything and filtering client-side.

## Decision: mirror `Flight.findByAirline` exactly, not the older Country→Airport pattern

Two precedents already exist for "list child entities of a parent" in this codebase:

- `Airport`/`Airline`'s `findByCountry` (`plans/add-country-airports-finder.md`) —
  checks the parent `Country` exists first and 404s `CountryNotFound` if not, since
  `Country` and `Airport`/`Airline` are linked only by relationship (no FK field on the
  child entity itself).
- `Flight.findByAirline` (`GET /api/v1/airlines/{icao}/flights`) — no existence check at
  all; `FindFlightsByAirlineService.findByAirline` calls straight through to
  `FlightRepository.findByAirline`, which joins on `airline_id` and simply returns `[]`
  for an airline with no flights (unknown vs. zero-results isn't distinguished).

**`Aircraft` is structurally identical to `Flight` here, not to `Airport`/`Airline`**:
both `Aircraft.airlineIcao` and `Flight.airlineIcao` are FK fields stored directly on the
entity (unlike `Airport`/`Airline`'s relationship-only link to `Country`). Mirror
`Flight.findByAirline`'s no-existence-check shape for consistency with the closer,
more-recent precedent — an unknown/typo'd ICAO code returns `200 []`, not `404`.

## Implementation

- **`domain/aircraft/FindAircraftByAirlineUseCase.scala`** (new):
  ```scala
  trait FindAircraftByAirlineUseCase:
    def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Aircraft]]
  ```
- **`domain/aircraft/AircraftRepository.scala`** — add
  `def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Aircraft]]`.
- **`application/aircraft/FindAircraftByAirlineService.scala`** (new) — implements the
  use case, delegates straight to `repo.findByAirline`, `ServiceAspect.logged`-wrapped
  (exact shape of `FindFlightsByAirlineService`).
- **`infrastructure/persistence-quill/.../aircraft/QuillAircraftRepository.scala`** —
  add `findByAirline`: join `aircraft` to `airlines` via the existing `AirlineRef`
  (from the already-mixed-in `QuillAirlineIdResolver`), filter `l.icaoCode == lift(icao.value)`,
  sort by registration, paginate with `.drop`/`.take` — same shape as `findAll`, and the
  same join pattern `QuillFlightRepository.findByAirline` already uses.
- **`adapter-http/aircraft/AircraftEndpoints.scala`** — add `findByAirline`:
  `GET /api/v1/airlines/{icao}/aircraft`, reusing `FlightEndpoints`'s `icaoParam`-style
  path validator (3-letter ICAO), `page`/`pageSize` query params, tag `"Aircraft"`
  (response type, matching the `add-country-airports-finder.md` tagging precedent).
  `errorOut`: unauthorized + unexpected only — no 404 variant, per the decision above.
- **`adapter-http/aircraft/AircraftRoutes.scala`** — add
  `findByAirlineSvc: FindAircraftByAirlineUseCase` constructor param, wire the endpoint,
  extend the `layer` `URLayer`.
- **`adapter-http/ApiSpec.scala`** — add the new endpoint to `allEndpoints`.
- **`bootstrap/WiringModule.scala`** — extend the aircraft use-case layers with
  `aircraftRepoLayer >>> FindAircraftByAirlineService.layer`.

## Tests

- `application/.../aircraft/FindAircraftByAirlineServiceSpec.scala` (new) — stub
  repository, assert delegation with the exact `icao`/`pagination` passed in.
- `adapter-http/.../aircraft/AircraftEndpointsSpec.scala` — new suite for
  `GET /api/v1/airlines/{icao}/aircraft`: 200 with list, 200 `[]` for an
  airline with no aircraft, 400 for a malformed ICAO code, 401 without a token.
- `infrastructure/integration-tests/.../quill/QuillAircraftRepositoryItSpec.scala` (or
  its contract spec) — add a `findByAirline` case: returns only the matching airline's
  rows, `[]` for an airline with none.

## After implementation

- `sbt scalafmtAll` → `sbt compile` (zero warnings) → `sbt test` → `integrationTests/test`.
- Manual curl verification against the live app using real data already seeded from the
  OpenSky import: `GET /api/v1/airlines/IBE/aircraft` should return Iberia's 81 aircraft.
- `docs/api/endpoint-status.md` — add
  `Aircraft | GET | /api/v1/airlines/{icao}/aircraft | ✓ implemented`.
