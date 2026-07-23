# Redesign Airline: drop foundationDate, add alias/callsign

> **Status:** Implemented and verified (unit tests, integration tests against real Postgres, OpenAPI
> validation, Postman/Newman against a live server).

## Goal

Building Airline sync (`plans/masterdata/airline-sync.md`) surfaced a real mismatch: OpenFlights'
`airlines.dat` — confirmed against OpenFlights' own schema docs (openflights.org/data.php) and the
live file — has exactly 8 fields (Airline ID, Name, Alias, IATA, ICAO, Callsign, Country, Active) and
**no date field of any kind**. `airlines.foundation_date` was `DATE NOT NULL` with no default
(`V9__add_airline_foundation_date.sql`) and `Airline.foundationDate: LocalDate` wasn't optional either
— so every synced airline needed *some* date to satisfy the column, with nothing in the source to
supply one.

Given a choice between a workaround (a sentinel date, flagged for manual backfill) and fixing the
mismatch at its root, **the decision was to drop `foundationDate` entirely and model `alias`/
`callsign` instead** — two fields the source actually provides that `Airline` didn't capture before.
This is a change to the already-shipped Airline entity's public contract, not just the sync tool, so
it gets its own plan doc separate from `plans/masterdata/airline-sync.md`.

## Decisions

- **`Airline(icao, name, alias: Option[String], callsign: Option[String])`** — no Newtype for the two
  new fields (plain optional strings, no shape rule), matching `Flight.alias`'s existing precedent
  (`flights.alias VARCHAR(8)`, nullable).
- **Migration `V15__redesign_airline_columns.sql`**: `DROP COLUMN foundation_date`, `ADD COLUMN alias
  VARCHAR(100)`, `ADD COLUMN callsign VARCHAR(100)` — both nullable. `VARCHAR(100)` matches this
  project's generous-sizing convention for free-text columns (`name` columns are 100–200); live
  OpenFlights data's longest alias is 30 chars, longest callsign 50 (the 50 is a known
  data-corruption artifact — a handful of rows have an unescaped comma inside `Callsign` that spills
  into `Country`, confirmed during `airline-sync.md`'s research; real callsigns are short call-words).
- **`CreateAirlineCommand`/`UpdateAirlineCommand`** gain the same two fields in place of
  `foundationDate`. `CreateAirlineRequest.toCommand`'s unguarded `LocalDate.parse(req.foundationDate)`
  (never wrapped in `ZIO.attempt` — confirmed during research as a real, if unexercised, defect risk:
  a malformed date string would have died as an untyped defect, not failed with a `DomainError`) is
  removed entirely along with the field, not just left unguarded.
- **`Flight.findAirlineByCode`'s join path** (both `QuillFlightRepository`/`DoobieFlightRepository`)
  reconstructs a full `Airline` value from a `flights ⋈ airlines` query — updated to select
  `alias`/`callsign` instead of `foundation_date`, same mechanical shape as every other Airline
  reconstruction site.

## Files touched

**New:**
- `infrastructure/migration/src/main/resources/db/migration/V15__redesign_airline_columns.sql`

**Edited — main sources:**
- `domain/.../airline/Airline.scala`, `CreateAirlineUseCase.scala`, `UpdateAirlineUseCase.scala`
- `application/.../airline/CreateAirlineService.scala`, `UpdateAirlineService.scala`
- `infrastructure/persistence-quill/.../airline/QuillAirlineRepository.scala`
- `infrastructure/persistence-postgres/.../airline/DoobieAirlineRepository.scala`
- `adapter-http/.../airline/AirlineDto.scala`
- `infrastructure/persistence-quill/.../flight/QuillFlightRepository.scala`,
  `infrastructure/persistence-postgres/.../flight/DoobieFlightRepository.scala` — `findAirlineByCode`'s
  reconstruction, found by the compiler (not by grepping for `foundationDate`, since these call sites
  use positional constructor args)

**Edited — tests (mechanical fixups, no new test cases):**
- `application/src/test/.../airline/AirlineServiceSpec.scala`
- `adapter-http/src/test/.../airline/AirlineEndpointsSpec.scala` (2 fixtures + 11 JSON body literals)
- `application/src/test/.../flight/FlightServiceSpec.scala`,
  `adapter-http/src/test/.../flight/FlightEndpointsSpec.scala`,
  `application/src/test/.../route/RouteServiceSpec.scala` — three more Airline-fixture sites the
  compiler caught that a text grep for `foundationDate` would have missed (positional args)
- `infrastructure/integration-tests/src/test/.../support/AirlineRepositoryContractSpec.scala`,
  `AircraftRepositoryContractSpec.scala`, `FlightRepositoryContractSpec.scala` — the shared
  contract specs both `Doobie*ItSpec`/`Quill*ItSpec` wrappers delegate to; neither wrapper file
  itself references `Airline` directly
- `infrastructure/integration-tests/src/test/.../migration/FlywayMigrationItSpec.scala` — asserted
  the schema reaches exactly `"14"`; bumped to `"15"` once the new migration landed (also caught by
  actually running the suite, not by grepping)

**Edited — docs:**
- `docs/analysis/01-domain-model.md` — Airline's entity-table row, the foundation-date
  invariants-table row (now the alias/callsign row)
- `docs/todo/master-data/analysis.md` — §2.3 (Alias/Callsign now mapped; the foundation-date gap
  marked resolved-by-removal, not defaulted), §9
- `plans/seed-data-airlines.sql` — drops `foundation_date` from every `INSERT`, supplies real
  callsigns for the 8 seeded airlines

**Edited — API contract propagation:**
- `docs/api/collection.json` — regenerated via `sync-postman-collection` (36 endpoint requests
  updated); one hand-written E2E test script (`pm.test('foundationDate is...', ...)`) and its two
  request bodies needed a manual follow-up fix since the sync skill only mirrors generated shape, not
  custom JS assertions

## Verification

1. `sbt scalafmtAll` then `sbt compile` (whole build) — zero errors, zero warnings.
2. `sbt "testOnly *"` — 343 tests, unchanged (mechanical fixups only, no new cases).
3. `sbt integrationTests/test` — 112 tests green against real Postgres with the new schema (one
   expected failure caught first: `FlywayMigrationItSpec`'s hardcoded `"14"`, fixed to `"15"`).
4. `/validate-openapi` skill — PASSED, 0 Redocly errors, 0 inline schemas, 0 Spectral errors.
5. `sync-postman-collection` skill — synced cleanly; Newman run against a live server caught the one
   real regression (the hand-written `foundationDate` assertion), fixed, then verified directly via a
   manual `curl` CRUD lifecycle against the live HTTP API (create with alias/callsign → get → update →
   delete, all correct) rather than re-running the full E2E suite, since the dev Postgres instance's
   other E2E fixtures (`PT`/`KI` country codes) already legitimately exist from earlier, unrelated
   Country-sync testing in the same session — a pre-existing environmental state, not a regression
   from this change.
