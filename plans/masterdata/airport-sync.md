# Implement Airport sync: AirportCsvParser, AirportSync, wiring into Main

> **Status:** Implemented and verified live against a real local Postgres.

## Goal

Country sync (`plans/masterdata/master-data-sync-scaffold.md` through
`plans/masterdata/country-sync-wiring.md`) is fully wired end-to-end. This change does the same for
Airport in one pass rather than five separate increments: `AirportCsvParser` (OurAirports CSV via
`scala-csv`, §2.2/§4.2), `AirportSync` (reusing the existing Airport domain/application/persistence
layer exactly the way `CountrySync` reuses Country's), and wiring both into `Main` alongside Country.

**Verified live** against the local dev DB (schema present, 249 countries already synced, 0 airports):
a run created 4,534 airports and logged
`created: 4534, updated: 0, deleted: 0, unchanged: 0, skippedInvalid: 0, skippedConflict: 1`
(the one skipped conflict is Priština's airport — OurAirports tags it `iso_country = "XK"` for Kosovo,
which isn't a real ISO 3166-1 alpha-2 code and so isn't in `countries`; correctly caught by
`EntitySync.apply`'s per-row catch-and-log rather than aborting the run). A second run against the
now-4,534-row table reported `unchanged: 4534` with zero writes — idempotent, matching Country's own
verification precedent.

## Decisions

- **ICAO code source: prefer `icao_code`, fall back to `ident` when blank.** The analysis doc's
  original §2.2 sketch (`ident → Airport.icaoCode`) predates confirming, against the live source, that
  OurAirports' `airports.csv` has a *dedicated* `icao_code` column separate from `ident`. `ident` is
  OurAirports' own row identifier — for ~126 large/medium airports it holds a non-ICAO-shaped internal
  id (e.g. `"AE-0221"`) while `icao_code` holds the real 4-letter code. Preferring `icao_code`, falling
  back to `ident` only when `icao_code` is blank, recovered 4,535 of 5,276 large/medium rows in
  pre-implementation verification against the live 85,797-row file, vs. ~4,294 using `ident` alone.
  §2.2 corrected to match.
- **Type filter applied and exclusion is silent, not logged** — only `large_airport`/`medium_airport`
  rows are considered at all (§9's existing recommendation), matching Country's own precedent of the
  header line being "skipped explicitly... an expected line, not a data problem" (§2.1). Only genuine
  data-quality problems on an *included* row (blank `iata_code`, no valid ICAO shape in either column)
  get the `WARN`-and-skip treatment per §8.
- **`AirportCsvParser.parse` returns `Task[List[AirportRow]]`, not `IO[IOException, _]`** —
  `scala-csv`'s `CSVReader` can throw more than `IOException`, so the error channel is the wider
  `Throwable`; `AirportSync.sync`'s signature follows suit (`Throwable`, where `CountrySync.sync` has
  `IOException`). `AirportRow(iataCode, icaoCode, name, city, countryCode: String)` mirrors
  `CountryRow`'s shape (raw strings, validated later in `toCommand`).
- **`toCommand` mirrors `CreateAirportRequest.toCommand`** (`adapter-http/.../airport/AirportDto.scala`)
  exactly: `IataCode.validateAll`/`AirportIcaoCode.validateAll` (accumulating) folded into
  `DomainError.InvalidIataCode`/`InvalidAirportIcaoCode` via `.toEitherWith` + `ZIO.fromEither`;
  `countryCode = CountryCode.unsafeMake(row.countryCode)` — a reference field, never validated at this
  boundary per `CLAUDE.md`'s convention, since its validity is enforced downstream by `AirportSync`'s
  `save`/`update` call failing with `CountryNotFound` if the code doesn't resolve to a real country.
- **`findAllUnbounded` added to the whole Airport stack** (didn't exist yet, unlike Country's), but
  typed `IO[DomainError, List[Airport]]` — matching *Airport's own* existing convention (`findByIata`/
  `findAll`/`searchByName` are all `IO[DomainError, _]`, not `UIO` like Country's) rather than copying
  Country's `UIO` signature verbatim. Touches: `AirportRepository`, `FindAirportUseCase` (port
  signatures), `FindAirportService` (delegates via `@@ ServiceAspect.logged`, matching its sibling
  methods' style — not `.apply(...)`, since the return type stays `IO[DomainError,_]` rather than
  becoming `UIO`), `QuillAirportRepository` (real, mirrors `QuillCountryRepository.findAllUnbounded`'s
  un-clamped `sortBy(_.iataCode)` query), `DoobieAirportRepository` (kept schema-consistent, unwired,
  same treatment `DoobieCountryRepository` got).
  Adding an abstract method broke every direct trait implementer, found by grepping the *whole* repo,
  not just the `airport` package — one of the three is filed under `route`, easy to miss:
  `AirportRepositoryStub` (both `unimplementedAirportRepo` and `stubAirportRepo`'s new
  `onFindAllUnbounded` default param), `AirportEndpointsSpec`'s `defaultFind`/`notFoundFind`, and
  `RouteServiceSpec`'s `findAirportStub` helper. No new test *cases* needed for these three — mechanical
  compile-fixes for a trait signature change, same as `country-sync-wiring.md`'s precedent.
- **`AirportSync.sync` adapts `findAllUnbounded`'s `IO[DomainError, _]` to `EntitySync.loadExisting`'s
  expected `UIO[List[E]]` via `.orDieWith(e => new RuntimeException(e.toString))`, not `.orDie`** —
  `.orDie` requires the error channel to already be `Throwable`; `DomainError` isn't, so the explicit
  conversion is required. `EntitySync` itself is untouched.
- **Follow-up: the reconcile-can't-detect-a-country-only-change gap is fixed.** `Airport`'s case class
  still has no `countryCode` field (the relationship is resolved separately via
  `AirportRepository.save`/`.update`'s extra param), so `EntitySync.reconcile`'s `==` diff on bare
  `Airport` couldn't flag a source row whose *only* change was `iso_country`. Fixed by widening the
  comparable `E` type `AirportSync` passes into `EntitySync` from bare `Airport` to `(Airport,
  CountryCode)` — a plain tuple, structurally comparable via `==` for free, no new wrapper type needed.
  The *existing* side of that pair comes from a new bulk repository method,
  `AirportRepository.findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]]`
  (exposed via `FindAirportUseCase`, implemented as one JOIN query in `QuillAirportRepository`, kept
  schema-consistent in `DoobieAirportRepository`) — a single query, not a per-row `findCountryByIata`
  call for every existing row. The *source* side is built the same way, straight from the parsed
  commands. This let the old `countryCodeByIata` lookup map be deleted entirely — the country code now
  travels with the entity through `create`/`update` via tuple destructuring instead of a separate
  lookup. `EntitySync` itself needed no change. New trait method meant the same mechanical-fixup sweep
  as before: `AirportRepositoryStub`, `AirportEndpointsSpec`, `RouteServiceSpec`'s `findAirportStub`,
  plus `FindAirportService`'s delegate.
- **`Main.scala`** extends the existing `for` inside `ZIO.acquireRelease(...).flatMap { dir => ... }`
  with a second download+sync+log triple after Country's (Country-then-Airport ordering per §5.1/§6),
  plus a new `airportRepoLayer`/`airportUseCasesLayer` built exactly like the Country ones
  (`QuillDataSourceLayer.live >>> QuillAirportRepository.layer`, then `>>>` per service, `++`-combined).
  `airportUrl = "https://ourairports.com/data/airports.csv"` — confirmed live to currently 301-redirect
  to `davidmegginson.github.io/ourairports-data/airports.csv`, already covered by `HttpDownloader`'s
  existing generic `followRedirects`, no change needed there.

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirportCsvParser.scala`
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirportSync.scala`
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirportCsvParserSpec.scala`
  — 7 tests: well-formed row, `icao_code`-blank/`ident`-fallback, silent type-filter exclusion, blank
  `iata_code` skip, no-valid-ICAO-in-either-column skip, plus two `toCommand` tests (valid;
  `InvalidAirportIcaoCode`).
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/AirportSyncSpec.scala`
  — 4 tests: create-only, update-only, delete-only, and a filtered-out row tolerated without aborting.

**Edited:**
- `project/Versions.scala` / `project/Dependencies.scala` — `scalaCsv` (`com.github.tototoshi:scala-csv`
  `2.0.0`, confirmed on Maven Central as `scala-csv_3`).
- `build.sbt` — `masterDataSync`'s `libraryDependencies` gains `scalaCsv`. No `.dependsOn` change —
  already `.dependsOn(domain, application, persistenceQuill)`.
- `domain/.../airport/AirportRepository.scala`, `FindAirportUseCase.scala` — `findAllUnbounded`.
- `application/.../airport/FindAirportService.scala` — delegates.
- `infrastructure/persistence-quill/.../airport/QuillAirportRepository.scala` — real implementation.
- `infrastructure/persistence-postgres/.../airport/DoobieAirportRepository.scala` — kept
  schema-consistent.
- `application/test/.../airport/AirportRepositoryStub.scala`,
  `adapter-http/test/.../airport/AirportEndpointsSpec.scala`,
  `application/test/.../route/RouteServiceSpec.scala` — mechanical fixups for the new trait method.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/Main.scala`
  — real layer wiring + `AirportSync.sync` call, after Country's.
- `docs/todo/master-data/analysis.md` — status header, §2.2, §3.2, §5.1, §9.

## Verification

1. `sbt scalafmtAll` (root, then `masterDataSync/scalafmtAll` separately — it's outside root's
   aggregate) then `sbt compile` (whole build) — zero errors, zero warnings.
2. `sbt "testOnly *"` at root — 343 tests, unchanged (the `findAllUnbounded` mechanical fixups touch
   existing test files but add no new cases there).
3. `sbt masterDataSync/compile` then `sbt masterDataSync/test` — 33 tests green (22 existing + 7 new
   `AirportCsvParserSpec` + 4 new `AirportSyncSpec`).
4. `sbt bloopInstall` — regenerated for the new `scala-csv` dependency.
5. **Real end-to-end run against local Postgres** (`docker compose up -d postgres`; 249 countries
   already present, 0 airports beforehand): `sbt masterDataSync/run` (`JAVA_HOME` pinned to a Java 21
   install). Logged `created: 4534, updated: 0, deleted: 0, unchanged: 0, skippedInvalid: 0,
   skippedConflict: 1`. Confirmed via `psql`: `airports` row count `0` → `4534`; spot-checked
   `MAD`/`JFK`/`LHR` for correct `icao_code`/name/city. A second `sbt masterDataSync/run` logged
   `created: 0, updated: 0, deleted: 0, unchanged: 4534, skippedConflict: 1` — idempotent, zero writes.
