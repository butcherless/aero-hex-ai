# Plan: `GET /api/v1/airlines/search` (Search Airlines by Name)

## Goal

Add name search for Airlines, matching the capability Country and Airport already have. Airline
was the only one of the three missing it end-to-end — even the Postgres trigram index
(`V10__add_airline_name_index.sql`, `idx_airlines_name_trgm`) was already in place, unused.

## Decision: clone Airport's dedicated `/search` endpoint, not Country's inline `name` param

Two existing patterns to choose from:
- **Country**: folds an optional `name` query param into the `findAll` list endpoint
  (`.validateOption(Validator.minLength(3))`).
- **Airport**: a separate `GET /api/v1/airports/search?q=...` endpoint
  (`.validate(Validator.minLength(3))` on a required `String` param).

Airport's shape was the better fit: Airline's `findAll` doesn't take a `name` param today (unlike
Country's), and Airline already has sibling collection-style endpoints (`findByCountry`,
`findByRoute`) rather than one all-purpose list endpoint — a new `search` sibling matches that
shape better than retrofitting `findAll`'s signature. Rejected: folding `name` into `findAll` —
would require changing `findAll`'s existing signature and every caller/test of it.

## Implementation

`searchByName` was added to the existing `FindAirlineUseCase`/`AirlineRepository` (not a new
use-case class) since Airport's `searchByName` lives the same way — `AirlineRoutes`'s existing
`useCase: FindAirlineUseCase` constructor param already covers it, so no new `WiringModule` layer
line was needed; `searchByName` rides on the `FindAirlineService.layer` already wired into
`airlineUseCaseLayers`.

- `domain/.../airline/AirlineRepository.scala` / `FindAirlineUseCase.scala` — added
  `def searchByName(query: String): IO[DomainError, List[Airline]]`.
- `infrastructure/persistence-quill/.../airline/QuillAirlineRepository.scala` — `ILIKE '%query%'`
  via `infix`, sorted by `name`, `.orDie` (copied verbatim from `QuillAirportRepository.searchByName`).
- `application/.../airline/FindAirlineService.scala` — thin `ServiceAspect.logged` passthrough.
- `adapter-http/.../airline/AirlineEndpoints.scala` — `searchByName`: `base.get.in("search").in(query[String]("q").validate(Validator.minLength(3)))`, `List[AirlineDto]` response, `badRequestVariant` + `unexpectedError` error output — identical shape to `AirportEndpoints.searchByName`.
- `adapter-http/.../airline/AirlineRoutes.scala` — endpoint inserted right after `findAll` in
  `serverEndpoints` (matches Airport's ordering convention).
- `adapter-http/.../ApiSpec.scala` — `AirlineEndpoints.searchByName` inserted right after
  `AirlineEndpoints.findAll` in `allEndpoints` (required for the static `OpenApiGenerator` path,
  independent of `HttpServer`'s route wiring).

## Tests

- `adapter-http/.../airline/AirlineEndpointsSpec.scala` — `defaultFind`/`notFoundFind` stubs
  extended with `searchByName`; new `suite("GET /api/v1/airlines/search")` (200 match, 400 on
  <3-char query, 404 propagated from a failing use case); `AirlineRoutes.layer` endpoint-count
  assertion bumped 7 → 8.
- `application/.../airline/AirlineRepositoryStub.scala` — `onSearchByName` param added to
  `stubAirlineRepo`, plus the matching `unimplementedAirlineRepo` override.
- `application/.../airline/AirlineServiceSpec.scala` — `"searchByName delegates to the repository
  unchanged"` test added to the `FindAirlineService` suite.
- `infrastructure/integration-tests/.../support/AirlineRepositoryContractSpec.scala` — `"searchByName
  matches a case-insensitive substring"` contract test added (British Airways / "british"), mirroring
  `AirportRepositoryContractSpec`'s Fiumicino case.

## Verification performed

- `sbt scalafmtAll` (root + `integrationTests/scalafmtAll`, since that module isn't in root's
  aggregate) then `sbt compile` — zero errors/warnings.
- `sbt test` — 354 tests total (350 + 4 new: 1 application-layer delegation test, 3 endpoint
  tests), all green.
- `sbt integrationTests/test` — 58 tests total (57 + 1 new contract test), all green, real
  Postgres via Testcontainers.
- Live-verified against the real app: `GET /api/v1/airlines/search?q=...` returns matching
  airlines; `q` shorter than 3 characters returns 400.
- `validate-openapi` / `sync-postman-collection` skills run after implementation.

## Files touched

- `domain/src/main/scala/dev/cmartin/aerohex/domain/airline/AirlineRepository.scala`
- `domain/src/main/scala/dev/cmartin/aerohex/domain/airline/FindAirlineUseCase.scala`
- `infrastructure/persistence-quill/src/main/scala/dev/cmartin/aerohex/infrastructure/persistence/quill/airline/QuillAirlineRepository.scala`
- `application/src/main/scala/dev/cmartin/aerohex/application/airline/FindAirlineService.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/airline/AirlineEndpoints.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/airline/AirlineRoutes.scala`
- `adapter-http/src/main/scala/dev/cmartin/aerohex/adapter/http/ApiSpec.scala`
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/airline/AirlineEndpointsSpec.scala`
- `application/src/test/scala/dev/cmartin/aerohex/application/airline/AirlineRepositoryStub.scala`
- `application/src/test/scala/dev/cmartin/aerohex/application/airline/AirlineServiceSpec.scala`
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/support/AirlineRepositoryContractSpec.scala`
- `docs/api/endpoint-status.md`
- `docs/api/collection.json` (via `sync-postman-collection` skill)
- `CLAUDE.md` / `infrastructure/integration-tests/CLAUDE.md` (test-count fixes found incidentally
  while verifying — root's `112 tests` for `integrationTests/test` was already stale before this
  change; actual count is 58)
