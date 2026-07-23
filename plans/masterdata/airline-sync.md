# Implement Airline sync: AirlineCsvParser, AirlineSync, wiring into Main

> **Status:** Implemented and verified live against a real local Postgres. Completes the three
> in-scope entities — Country, Airport, Airline all sync end-to-end.

## Goal

Country and Airport sync are done (`plans/masterdata/country-sync-wiring.md`,
`plans/masterdata/airport-sync.md`). This closes out Airline, the last in-scope entity
(`docs/todo/master-data/analysis.md` §1) — mirroring `AirportSync`'s pattern closely, but building on
top of `plans/redesign-airline-drop-foundation-date.md`'s entity redesign (dropped `foundationDate`,
added `alias`/`callsign`) rather than needing any sentinel-value workaround.

**Verified live** against the local dev DB (0 airlines beforehand): a run created 1,009 airlines and
logged `created: 1009, updated: 0, deleted: 0, unchanged: 0, skippedInvalid: 17, skippedConflict: 7`.
A second run logged `created: 0, updated: 7, deleted: 0, unchanged: 1009, skippedInvalid: 17,
skippedConflict: 0` — the row count stayed at 1,009 (confirmed via `psql`), but 7 rows showed
`updated` instead of settling into `unchanged` (see the known nuance below).

## Decisions

- **No header row** (unlike Country/Airport's CSVs) — `CSVReader.open(file.toFile).all(): List[List[String]]`,
  fixed positional columns (`1`=name, `2`=alias, `4`=icao, `5`=callsign, `6`=country, `7`=active;
  `0`=id and `3`=IATA unused, `Airline` has no IATA field per §2.3).
- **`\N` and blank both mean "absent"** for `alias`/`callsign` → `None`.
- **`active == "Y"` filtered silently** — an expected scope exclusion (§2.3's existing
  recommendation), matching Country's header-skip / Airport's type-filter precedent. A row that's
  active but has no valid 3-letter ICAO shape is logged+skipped (§8) — a genuine data problem, not an
  intentional exclusion.
- **Country name → `CountryCode` resolution**: `AirlineSync.sync` pulls `FindCountryUseCase` (reusing
  exactly what `CountrySync` already needs — no new dependency) and builds a
  `Map[String, CountryCode]` from the live `Country` list, applying `AirlineCsvParser`'s static
  `countryNameAliases` table first for OpenFlights' known name variants. Unmapped names fail with
  `DomainError.CountryNotFound` (reusing the existing error — same semantic as `resolveCountryId`'s
  failure, no new `DomainError` case needed) and are logged+skipped, same as any other unresolvable
  row.
- **`AirlineSync.sync`'s environment is five services, not four**:
  `CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase & FindAirlineUseCase &
  FindCountryUseCase` — the one structural difference from `AirportSync` (Airport's `iso_country`
  column is already a code, no live lookup needed).
- **`findAllUnbounded` added to the whole Airline stack** (didn't exist yet), typed
  `IO[DomainError, List[Airline]]` matching Airline's own existing convention. Mechanical fixups for
  the new trait method: only **two** files this time (smaller than Airport's three) —
  `AirlineRepositoryStub.scala` and `AirlineEndpointsSpec.scala`'s `defaultFind`/`notFoundFind` — a
  whole-repo grep confirmed no `RouteServiceSpec`-style surprise for Airline.
- **Follow-up: fixed the same way as Airport.** `Airline`'s case class still has no `countryCode`
  field, so `EntitySync.reconcile`'s `==` diff couldn't detect a country-only change for an existing
  row. `AirlineSync` now reconciles `(Airline, CountryCode)` pairs instead of bare `Airline`, backed by
  a new `AirlineRepository.findAllUnboundedWithCountry` bulk join query (+ `FindAirlineUseCase`, Quill
  join implementation, Doobie kept schema-consistent) for the *existing* side — the old
  `countryCodeByIcao` lookup map is gone; the country code now travels with the entity itself.
- **New nuance found during live verification, not anticipated in the design**: OpenFlights' source
  itself has a handful of rows sharing the same ICAO code under different names (e.g. two distinct
  `JAL` entries). Since `EntitySync.reconcile`'s `toCreate`/`toUpdate` buckets are built by scanning
  the full source list against the *existing* map — not deduplicating within the source itself — both
  same-keyed rows get processed; whichever is created first wins the row, the second hits
  `AirlineAlreadyExists` and is counted as `skippedConflict` (exactly the generic per-row tolerance
  `EntitySync.apply` already provides, working correctly on a case the design didn't specifically
  anticipate). On a subsequent run, the *other* duplicate can look like an "update" against what's
  stored, since the source always contains both. Affects 7 of 1,009 rows — originally accepted as a
  source-data-quality quirk, not fixed.
- **Follow-up: fixed.** `AirlineSync.sync` now dedupes the parsed `commands` by ICAO (`groupBy` +
  keep-first) before calling `EntitySync.reconcile` at all — `EntitySync` itself stays untouched,
  since the dedup happens one layer up at the OpenFlights-specific call site. Every dropped duplicate
  is logged and counted as `skippedInvalid`. Covered by a new `AirlineSyncSpec` test ("keeps only the
  first row when the source has two entries for the same ICAO").

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirlineCsvParser.scala`
  — `AirlineRow(icao, name, alias, callsign, countryName)`, `parse`, `toCommand`, and the
  `countryNameAliases` static table (~35 entries, verified against live data; not exhaustive — see
  §9 of the analysis doc).
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirlineSync.scala`
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirlineCsvParserSpec.scala`
  — 7 tests: well-formed row, alias+callsign both present, silent inactive-filter, blank-ICAO skip,
  plus three `toCommand` tests (valid; `InvalidAirlineIcaoCode`; `CountryNotFound` for an unresolvable
  name).
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirlineSyncSpec.scala`
  — mirrors `AirportSyncSpec`'s `Ref`-backed stub pattern, plus a `FindCountryUseCase` stub; 4 tests
  (create/update/delete + a country-name-unresolvable tolerance test).

**Edited:**
- `domain/.../airline/AirlineRepository.scala`, `FindAirlineUseCase.scala` — `findAllUnbounded`.
- `application/.../airline/FindAirlineService.scala` — delegates.
- `infrastructure/persistence-quill/.../airline/QuillAirlineRepository.scala` — real implementation.
- `infrastructure/persistence-postgres/.../airline/DoobieAirlineRepository.scala` — kept
  schema-consistent.
- `application/test/.../airline/AirlineRepositoryStub.scala`,
  `adapter-http/test/.../airline/AirlineEndpointsSpec.scala` — mechanical fixups for the new trait
  method.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/Main.scala`
  — real layer wiring (`airlineRepoLayer`/`airlineUseCasesLayer`) + `AirlineSync.sync` call, after
  Airport's, providing `airlineUseCasesLayer ++ countryUseCasesLayer` (reusing the already-built
  Country layer for the `FindCountryUseCase` dependency).
- `docs/todo/master-data/analysis.md` — status header, §2.3, §3.2, §5.1, §9.

## Verification

1. `sbt scalafmtAll` (+ `masterDataSync/scalafmtAll`) then `sbt compile` — zero errors, zero warnings.
2. `sbt "testOnly *"` — 343 tests, unchanged (the `findAllUnbounded` fixups touch existing test files
   but add no new cases there).
3. `sbt masterDataSync/test` — 44 tests green (33 existing + 7 new `AirlineCsvParserSpec` + 4 new
   `AirlineSyncSpec`).
4. `sbt bloopInstall` — regenerated (no new dependency this time, `scala-csv` already added for
   Airport).
5. **Real end-to-end run against local Postgres** (`docker compose up -d postgres`; 0 airlines
   beforehand): `sbt masterDataSync/run` (`JAVA_HOME` pinned to Java 21). Logged
   `created: 1009, updated: 0, deleted: 0, unchanged: 0, skippedInvalid: 17, skippedConflict: 7`.
   Confirmed via `psql`: `airlines` row count `0` → `1009`; spot-checked `IBE`/`DLH`/`AFR` for correct
   `alias`/`callsign`. A second run logged `unchanged: 1009` (row count stable) with 7 rows showing
   `updated` instead — the duplicate-ICAO nuance above, not a correctness problem.
