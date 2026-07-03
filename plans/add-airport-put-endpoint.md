# Plan: `PUT /api/v1/airports/{iata}` (Update Airport)

## Goal

Add an update endpoint for airports, mirroring `UpdateCountryUseCase` /
`UpdateCountryService` / `CountryEndpoints.update`. Builds on the just-implemented
`POST /api/v1/airports` (see `plans/add-airport-post-endpoint.md`, now implemented) —
reuses the same `DomainError.CountryNotFound` FK-violation mapping decision made there.

## Current state

- `AirportRepository` (port-out) has **no `update` method at all** — unlike
  `CountryRepository`, which already had `update`. This is new surface area, not a
  stub-to-real swap.
- `airports.iata_code` is the primary key and is the path parameter — it is **not**
  itself updatable (same as Country: `code` is immutable, only `name` changes via PUT).
  For Airport there are more mutable fields to decide on (see below).
- `DoobieAirportRepository` has no upsert-style bug to worry about here — `UPDATE ...
  WHERE iata_code = ?` is a single atomic statement, same shape as
  `QuillCountryRepository.update` / `DoobieCountryRepository` — no race condition
  possible (existence check and mutation happen in the same statement, via affected-row
  count).
- `airports.country_code` has an FK constraint to `countries.code`. If `countryCode` is
  made updatable, an update to an unknown country hits the same FK-violation path as
  `create` (SQLState `23503`) and should reuse `DomainError.CountryNotFound`, consistent
  with the decision already made for `POST /api/v1/airports`.

## Design decision to confirm before implementing

**Which fields does PUT allow updating?** Two options:
1. **Only `name`** (tightest parity with `UpdateCountryRequest`, which only updates
   `name` because `code` is the only other field and it's the PK). Simplest, smallest
   surface area.
2. **All mutable fields** (`icaoCode`, `name`, `city`, `countryCode` — everything except
   the PK `iataCode`). More useful in practice (fixing a wrong city or ICAO code without
   a delete+recreate), but reintroduces the FK-violation-on-update case above and needs
   the same conflict/not-found error handling as create.

Recommend option 2 (full mutable-field update) since Airport has enough non-key fields
that a name-only PUT would be of limited use — but this is a product decision, not a
technical constraint, so confirm before implementing.

## Implementation steps (assuming option 2 — full update)

1. **Port-out** — `domain/.../port/out/AirportRepository.scala`: add
   `def update(airport: Airport): IO[DomainError, Airport]`.

2. **Port-in** — new file `domain/.../port/in/UpdateAirportUseCase.scala`:
   ```scala
   case class UpdateAirportCommand(iataCode: IataCode, icaoCode: String, name: String, city: String, countryCode: CountryCode)
   trait UpdateAirportUseCase:
     def update(command: UpdateAirportCommand): IO[DomainError, Airport]
   ```

3. **Application service** — new file
   `application/.../service/UpdateAirportService.scala`, mirroring
   `UpdateCountryService`: delegate straight to `repo.update(...)` — no pre-check needed,
   the single `UPDATE` statement's affected-row count already tells us "found" vs "not
   found" atomically. Add `ServiceAspect.logged(...)`.

4. **`DoobieAirportRepository.update`**: 
   ```scala
   sql"""UPDATE airports SET icao_code = ${a.icaoCode}, name = ${a.name}, city = ${a.city}, country_code = ${a.countryCode.value}
         WHERE iata_code = ${a.iataCode.value}"""
     .update.run
     .attemptSomeSqlState {
       case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(a.countryCode.value)
     }
     .transact(xa)
     .orDie
     .flatMap {
       case Left(error) => ZIO.fail(error)
       case Right(0L)   => ZIO.fail(DomainError.AirportNotFound(a.iataCode.value))
       case Right(_)    => ZIO.succeed(a)
     }
   ```
   Note the two failure modes both need checking: SQLSTATE for FK violation, *and*
   affected-row count for "no such airport" — order matters, check the `Either` first,
   then the row count on the `Right` branch.

5. **In-memory stub in `WiringModule`**: add
   `def update(a: Airport): IO[DomainError, Airport] = ZIO.succeed(a)` to the anonymous
   `AirportRepository` instance (never 404s against the stub, same caveat as `save`).

6. **DTO** — `adapter-http/.../dto/AirportDto.scala`: add `UpdateAirportRequest`
   (icaoCode, name, city, countryCode — no iata, it's the path param), `toCommand(iata,
   req)`, and a `Schema` with the same validators used in `CreateAirportRequest` minus
   the `iata` field.

7. **`AirportEndpoints.update`**: `PUT /api/v1/airports/{iata}`, reusing a validated
   `path[String]("iata")` capture (consider factoring the length/pattern validators
   already inlined in `findByIata`'s path capture into a shared `private val
   iataParam`, mirroring `CountryEndpoints.codeParam` — currently `findByIata` doesn't
   validate the path param at all, which is a pre-existing gap worth fixing while
   touching this file, confirm with user). `jsonBody[UpdateAirportRequest]`, `200` +
   `AirportDto`, `errorOut` with a 404 variant (covers both `AirportNotFound` and, if
   option 2, `CountryNotFound`) plus `unexpectedError`.

8. **`AirportRoutes`**: add `updateSvc: UpdateAirportUseCase` constructor param, wire
   `AirportEndpoints.update.zServerLogic`, extend the `layer` URLayer.

9. **`ApiSpec.allEndpoints`**: add `AirportEndpoints.update` — same reminder as before,
   this list is separate from `AirportRoutes`/`WiringModule` and easy to miss.

10. **`WiringModule.appLayer`**: extend `airportUseCaseLayers` (already introduced in
    the create-endpoint work) with `(airportRepoLayer >>> UpdateAirportService.layer)`.

11. **Tests**: extend `AirportEndpointsSpec` with a `PUT /api/v1/airports/{iata}` suite
    (200/404/400 cases), following the same stub-use-case pattern as the `POST` suite
    already there.

12. **Docs**: update the CLAUDE.md API table row for
    `Airports | PUT | /api/v1/airports/{iata}`, using the same "logic implemented,
    bootstrap still uses in-memory stub" phrasing established for the POST row.

13. **After implementing**: `sbt scalafmtAll && sbt compile` (zero warnings), then run
    the `sync-postman-collection` skill (mandatory — Tapir endpoints changed).

## Files touched (summary)

- `domain/.../port/out/AirportRepository.scala`
- `domain/.../port/in/UpdateAirportUseCase.scala` (new)
- `application/.../service/UpdateAirportService.scala` (new)
- `infrastructure/persistence-postgres/.../DoobieAirportRepository.scala`
- `adapter-http/.../dto/AirportDto.scala`
- `adapter-http/.../endpoint/AirportEndpoints.scala`
- `adapter-http/.../endpoint/AirportRoutes.scala`
- `adapter-http/.../ApiSpec.scala`
- `bootstrap/.../WiringModule.scala`
- `adapter-http/src/test/.../AirportEndpointsSpec.scala`
- `CLAUDE.md` (API table row)
- `docs/api/collection.json` / `docs/api/environment.json` (via sync skill)
