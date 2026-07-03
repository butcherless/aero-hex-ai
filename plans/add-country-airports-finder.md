# Plan: `GET` — list airports belonging to a country

## Goal

Add a finder that returns all airports in a given country (e.g. "all airports in
Spain"). Two viable shapes exist; this plan lays out both and recommends one, but the
choice affects routing/tagging enough that it should be confirmed before implementing.

## Design decision to confirm before implementing

### Option A — nested resource path (recommended)

`GET /api/v1/countries/{code}/airports`

- Reads as "the airports collection of this country" — idiomatic REST for a
  one-to-many relationship, and discoverable from the Country resource.
- Requires deciding whether an unknown `{code}` 404s (parent-resource-not-found
  semantics) or silently returns `[]` (treat it as a plain filter). Recommend 404 —
  consistent with how `GET /api/v1/countries/{code}` already 404s on
  `CountryNotFound`, and avoids a client silently getting an empty list for a typo'd
  country code with no way to tell "no airports" apart from "no such country."
  This means the use case needs read access to **both** `CountryRepository` (existence
  check) and `AirportRepository` (the actual query) — a new cross-aggregate use case,
  not just an addition to `FindAirportUseCase`.
- Tapir tag: could go under `"Airports"` (response type is `AirportDto`) or
  `"Countries"` (path root is `/countries`). Recommend `"Airports"` — Tapir's
  `folderStrategy=Tags` groups the generated Postman collection by tag, and grouping by
  response resource type keeps all airport-returning endpoints together. No strong
  precedent either way in this codebase yet, so this is a one-time convention call.

### Option B — query parameter on the existing collection endpoint

`GET /api/v1/airports?countryCode=ES`

- Simpler: extends `AirportEndpoints.findAll` with an optional query param, no new
  cross-aggregate use case, no 404-vs-empty-list ambiguity (a filter naturally returns
  `[]` for "no matches", whether that's because the country code is unknown or just has
  no airports — consistent with how `searchByName` already behaves for a query with no
  matches).
- Downside: conflates "list everything" and "list by country" into one endpoint with
  optional-parameter branching logic, and doesn't validate the country code format at
  all unless it's given a dedicated validated query parameter (mirroring
  `AirportDto`'s countryCode validators).

**Recommendation: Option A**, for symmetry with how `codeParam`/path-based lookups are
already used elsewhere (`findByCode`, `findByIata`), and because "airports of a
country" reads more clearly as a distinct resource than as a filter flag. Confirm
before implementing — this is the one real judgment call in this plan.

## Implementation steps (assuming Option A)

1. **Port-in** — new file `domain/.../port/in/FindAirportsByCountryUseCase.scala`:
   ```scala
   trait FindAirportsByCountryUseCase:
     def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
   ```
   (Kept as its own trait rather than folded into `FindAirportUseCase`, since it needs
   `CountryRepository` as a dependency and `FindAirportService` currently only depends
   on `AirportRepository` — mirrors how `CreateRouteService` already depends on two
   repositories when a use case spans aggregates.)

2. **Application service** — new file
   `application/.../service/FindAirportsByCountryService.scala`: depends on
   `CountryRepository & AirportRepository`. Logic:
   ```scala
   countryRepository.findByCode(code).flatMap {
     case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
     case Some(_) => airportRepository.findByCountry(code, pagination) // new repo method, see step 3
   }
   ```

3. **Port-out** — `domain/.../port/out/AirportRepository.scala`: add
   `def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]`.

4. **`DoobieAirportRepository.findByCountry`**:
   ```sql
   SELECT iata_code, icao_code, name, city, country_code FROM airports
   WHERE country_code = ? ORDER BY iata_code LIMIT ? OFFSET ?
   ```
   Same shape as the existing `findAll`, just with a `WHERE` clause — the
   `idx_airports_country` index (from the V2 migration) already covers this.

5. **In-memory stub in `WiringModule`**: add
   `def findByCountry(code: CountryCode, p: Pagination): IO[DomainError, List[Airport]] = ZIO.succeed(Nil)`.

6. **`AirportEndpoints.findByCountry`**: `GET /api/v1/countries/{code}/airports`, reuse
   the same length/pattern path validators as `CountryEndpoints.codeParam` (factor into
   a shared validator if one doesn't already exist accessible from `AirportEndpoints` —
   `codeParam` is currently `private` in `CountryEndpoints`), plus `page`/`pageSize`
   query params mirroring `findAll`. `errorOut` needs a 404 variant for
   `CountryNotFound` plus `unexpectedError`.

7. **`AirportRoutes`**: add `findByCountrySvc: FindAirportsByCountryUseCase`
   constructor param, wire the new endpoint, extend the `layer` URLayer.

8. **`ApiSpec.allEndpoints`**: add the new endpoint — same reminder as the last two
   plans, easy to miss since it's a separate hardcoded list.

9. **`WiringModule.appLayer`**: extend `airportUseCaseLayers` with
   `((airportRepoLayer ++ countryRepoLayer) >>> FindAirportsByCountryService.layer)` —
   note this is the **real** Quill-backed `countryRepoLayer`, not a stub, so this
   endpoint's 404 behavior is actually exercisable end-to-end even though the airport
   side is still stubbed to `Nil`.

10. **Tests**: new suite in `AirportEndpointsSpec` (or a dedicated spec if it grows) for
    `GET /api/v1/countries/{code}/airports` — 200 with list, 404 for unknown country,
    400 for malformed code.

11. **Docs**: add a new row to the CLAUDE.md API table:
    `Airports | GET | /api/v1/countries/{code}/airports | stub (real 404 check against Quill-backed Country repo; airport list itself is stub)`.

12. **After implementing**: `sbt scalafmtAll && sbt compile` (zero warnings), then run
    the `sync-postman-collection` skill.

## Files touched (summary)

- `domain/.../port/in/FindAirportsByCountryUseCase.scala` (new)
- `application/.../service/FindAirportsByCountryService.scala` (new)
- `domain/.../port/out/AirportRepository.scala`
- `infrastructure/persistence-postgres/.../DoobieAirportRepository.scala`
- `adapter-http/.../endpoint/AirportEndpoints.scala`
- `adapter-http/.../endpoint/AirportRoutes.scala`
- `adapter-http/.../ApiSpec.scala`
- `bootstrap/.../WiringModule.scala`
- `adapter-http/src/test/.../AirportEndpointsSpec.scala`
- `CLAUDE.md` (API table row)
- `docs/api/collection.json` / `docs/api/environment.json` (via sync skill)
