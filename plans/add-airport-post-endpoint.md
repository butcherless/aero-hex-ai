# Plan: `POST /api/v1/airports` (Create Airport)

## Goal

Add a create endpoint for airports, mirroring the existing Country create flow
(`CreateCountryUseCase` / `CreateCountryService` / `CountryEndpoints.create`), while
avoiding the non-atomic-upsert bug that was just fixed on the Country side
(see commit `6519046`, "Fix Country creation to insert instead of upsert").

## Current state (as of this plan)

- `AirportRepository.save` and `.delete` already exist in the port-out trait, but
  nothing calls `save` except the trait's own implementations — there is no create
  use case yet, so this is genuinely new wiring, not a stub-to-real swap.
- **`DoobieAirportRepository.save` already has the same upsert bug** Country had:
  ```sql
  INSERT INTO airports (iata_code, icao_code, name, city, country_code)
  VALUES (...)
  ON CONFLICT (iata_code) DO UPDATE
    SET icao_code = EXCLUDED.icao_code, name = EXCLUDED.name, city = EXCLUDED.city
  ```
  This must become a plain `INSERT` as part of this work — otherwise the new create
  endpoint inherits the exact silent-overwrite race the Country fix just closed.
- `airports.iata_code` is `PRIMARY KEY`; `airports.country_code` is
  `REFERENCES countries (code)` (V2 migration) — so two distinct failure modes exist
  on insert: PK violation (duplicate IATA code) and FK violation (unknown country
  code). Both need distinct handling (see below).
- `persistence-postgres` (where `DoobieAirportRepository` lives) is **not wired into
  bootstrap** per CLAUDE.md — `WiringModule` uses an in-memory stub for
  `AirportRepository` where `save` always succeeds and `findByIata` always returns
  `None`. The new use case will work correctly against that stub (every create
  "succeeds", nothing is persisted) but won't demonstrate the conflict path until/unless
  `persistence-postgres` gets wired in. That's an existing, separate gap — not something
  this plan needs to fix, just be aware of when demoing.
- `DomainError` has no `AirportAlreadyExists` case yet (only `AirportNotFound`).

## Design decisions to confirm before implementing

1. **FK violation mapping**: when `country_code` doesn't reference an existing country,
   should this be a `400 Bad Request` (invalid input) or `404 Not Found` (referenced
   resource doesn't exist)? Recommend reusing `DomainError.CountryNotFound(countryCode)`
   → `404`, consistent with how `CountryNotFound` is already mapped elsewhere. Confirm
   with user before implementing — this is the one real judgment call in this plan.
2. **IATA code casing**: like `CountryCode`, `IataCode` has no normalization. Decide
   whether to uppercase on construction (recommended, avoids `"MAD"` vs `"mad"`
   duplicates) or leave as-is for parity with existing `CountryCode` behavior (already
   has the same latent issue, undocumented as a decision — not in scope to fix here,
   just don't make it worse).

## Implementation steps

1. **`DomainError`** (`domain/.../error/DomainError.scala`)
   Add `case class AirportAlreadyExists(iata: String) extends DomainError`.

2. **`ErrorMapper`** (`adapter-http/.../error/ErrorMapper.scala`)
   Add `case AirportAlreadyExists(iata) => ApiError(StatusCode.Conflict, s"Airport already exists: $iata")`.

3. **Port in** — new file `domain/.../port/in/CreateAirportUseCase.scala`:
   ```scala
   case class CreateAirportCommand(iata: IataCode, icaoCode: String, name: String, city: String, countryCode: CountryCode)
   trait CreateAirportUseCase:
     def create(command: CreateAirportCommand): IO[DomainError, Airport]
   ```

4. **Application service** — new file `application/.../service/CreateAirportService.scala`,
   mirroring `CreateCountryService`: check `findByIata`, fail with `AirportAlreadyExists`
   if present, else `repo.save(...)`. Add `ServiceAspect.logged(...)` for parity.
   (Note: as with Country, this app-level check is still just a nice error message for
   the common case — the DB constraint via step 5 is the actual correctness guarantee
   under concurrency.)

5. **Fix `DoobieAirportRepository.save`** to a plain `INSERT`, and
   `refineOrDie`/`attemptSomeSqlState`-equivalent to map:
   - unique violation (SQLState `23505`) → `DomainError.AirportAlreadyExists(iata)`
   - foreign-key violation (SQLState `23503`) → `DomainError.CountryNotFound(countryCode)`
     (pending decision #1 above)
   Doobie note: use `.attemptSomeSqlState` (Doobie's typed SQLSTATE matcher) rather than
   catching `SQLException` directly, since this is Doobie not Quill — check Doobie 1.0.0
   docs via Context7 for the exact combinator name/signature before writing this, per
   CLAUDE.md's "fetch current docs" rule (Doobie 1.x has breaking changes from prior
   versions).

6. **In-memory stub in `WiringModule`**: add nothing functionally new — `save` already
   exists and returns the input unchanged, which is correct create-stub behavior.

7. **DTO** — `adapter-http/.../dto/AirportDto.scala`: add `CreateAirportRequest` case
   class + companion (`toCommand`, `Schema` with validators), mirroring
   `CreateCountryRequest`:
   - `iata`: length 3, pattern `[a-zA-Z]{3}`
   - `icaoCode`: length 4, pattern `[a-zA-Z]{4}`
   - `name`, `city`: non-empty (`Validator.minLength(1)`)
   - `countryCode`: length 2, pattern `[a-zA-Z]{2}`

8. **`AirportEndpoints.create`**: `POST /api/v1/airports`, `jsonBody[CreateAirportRequest]`,
   `201 Created` + `CountryDto`-style body + `Location` header, `errorOut` with both a
   409 conflict variant and (per decision #1) a 404 variant for unknown country, plus
   `unexpectedError`.

9. **`AirportRoutes`**: add `createSvc: CreateAirportUseCase` constructor param, wire
   `AirportEndpoints.create.zServerLogic`, update the `layer` URLayer signature.

10. **`ApiSpec.allEndpoints`**: add `AirportEndpoints.create` — **do not skip this**,
    it's a separate hardcoded list from `AirportRoutes`/`WiringModule` and it's exactly
    what got missed on the first pass of the search-endpoint work just done in this
    session. The OpenAPI generator only sees what's in this list.

11. **`WiringModule.appLayer`**: extend `airportRepoLayer >>> FindAirportService.layer >>>
    AirportRoutes.layer` to also thread `CreateAirportService.layer` in, matching how
    Country combines `FindCountryService`/`CreateCountryService`/etc. via `++` before
    `>>> CountryRoutes.layer`.

12. **Tests**: no `AirportEndpointsSpec` exists yet (unlike `CountryEndpointsSpec`).
    Consider adding one mirroring `CountryEndpointsSpec`'s structure (stub use cases,
    `TapirStubInterpreter`, one `suite` per endpoint) — at minimum covering the new
    `POST /api/v1/airports` success/409/400 cases. Confirm with user whether to also
    backfill tests for the pre-existing `findAll`/`findByIata`/`searchByName` endpoints
    while touching this file, or keep the diff scoped to `create` only.

13. **Docs**: update the CLAUDE.md REST API table row for
    `Airports | POST | /api/v1/airports` from (currently just missing) to `✓ implemented`
    — check whether "implemented" is accurate given `persistence-postgres` still isn't
    wired into bootstrap (the Country row's precedent: Country POST is marked
    `✓ implemented` and it *is* wired to real Postgres via Quill). Airports would only be
    "implemented against a stub" — flag this nuance rather than silently copying the
    Country table's wording.

14. **After implementing**: run `sbt scalafmtAll && sbt compile` (zero warnings gate per
    CLAUDE.md), run the `sync-postman-collection` skill (mandatory after any Tapir
    endpoint change per its trigger rules), and consider `validate-openapi` to confirm
    the generated spec is still well-formed.

## Files touched (summary)

- `domain/.../error/DomainError.scala`
- `domain/.../port/in/CreateAirportUseCase.scala` (new)
- `application/.../service/CreateAirportService.scala` (new)
- `infrastructure/persistence-postgres/.../DoobieAirportRepository.scala`
- `adapter-http/.../error/ErrorMapper.scala`
- `adapter-http/.../dto/AirportDto.scala`
- `adapter-http/.../endpoint/AirportEndpoints.scala`
- `adapter-http/.../endpoint/AirportRoutes.scala`
- `adapter-http/.../ApiSpec.scala`
- `bootstrap/.../WiringModule.scala`
- `adapter-http/src/test/.../AirportEndpointsSpec.scala` (new, pending confirmation)
- `CLAUDE.md` (API table row)
- `docs/api/collection.json` / `docs/api/environment.json` (via sync skill, not by hand)
