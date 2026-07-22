# Wire CountrySync into Main: real Postgres create/update/delete

> **Status:** Implemented and verified live against a real local Postgres.

## Goal

`EntitySync`/`SyncReport` (`plans/masterdata/entity-sync.md`) were generic and unit-tested standalone,
but nothing called them yet — `Main` only downloaded and parsed the Country CSV, logging a row count.
This change wires the missing piece end-to-end: `CountrySync` adapts `Country` rows to the existing
`CreateCountryService`/`UpdateCountryService`/`DeleteCountryService`/`FindCountryService` and drives
`EntitySync`, and `Main` composes the real Quill-backed layers (the same pattern `bootstrap`'s
`WiringModule` already uses) instead of only downloading.

**This is the first change in this tool that actually writes to Postgres.** Verified live against the
local dev DB (schema already present via prior Flyway runs; started with 1 row, `ES, Spain`): a run
created the other 248 countries (`created: 248, updated: 0, deleted: 0, unchanged: 1`), preserving all
4 quoted-comma edge cases (`Bonaire, Sint Eustatius and Saba`, `Palestine, State of`,
`Saint Helena, Ascension and Tristan da Cunha`, `Tanzania, the United Republic of`); a second run
against the now-249-row table reported `unchanged: 249` with zero writes, confirming idempotency.

## Decisions

- **`findAllUnbounded` added to both `CountryRepository` (port/out) and `FindCountryUseCase`
  (port/in)**, per §7.1's original recommendation. Implemented in `QuillCountryRepository` (an
  un-clamped `querySchema[CountryRow]("countries").sortBy(_.code)`, dropping `findAll`'s
  `.drop(...).take(...)` pagination but keeping the deterministic sort) and, kept schema-consistent
  per `CLAUDE.md`, in `DoobieCountryRepository` (same query minus `LIMIT`/`OFFSET`). `FindCountryService`
  delegates with the same `ServiceAspect.logged` wrapping its other methods use.
- **Adding an abstract method to two existing traits required updating every implementer**, not just
  the wired Quill path: `DoobieCountryRepository` (unwired but schema-consistent), the two test fixtures
  that implement these traits directly — `application`'s `CountryRepositoryStub` (`unimplementedCountryRepo`
  dies unless overridden, `stubCountryRepo` gained an `onFindAllUnbounded` parameter) and `adapter-http`'s
  `CountryEndpointsSpec` (`defaultFind`/`notFoundFind`). No new test *cases* needed here — these are
  mechanical compile-fixes for a trait signature change, not new behavior to verify.
- **`CountrySync.sync(file: Path)` requires `CreateCountryUseCase & UpdateCountryUseCase &
  DeleteCountryUseCase & FindCountryUseCase`** in its environment (not a repository directly) — it
  reuses the same use-case layer every other driving adapter (HTTP) goes through, so validation/
  persistence rules stay identical regardless of caller. Body: parse → `toCommand` each row via
  `.either` (never aborts on one bad row) → build a `Country` from every `Right` → `EntitySync.loadExisting`
  via `findUseCase.findAllUnbounded` → `reconcile` → `apply` with closures that build
  `CreateCountryCommand`/`UpdateCountryCommand` from a `Country` and call the real use case (`.unit`-
  discarding the returned `Country`), `delete` passed straight through. The returned `SyncReport`'s
  `skippedInvalid` is `.copy`-overwritten with the count of `Left`s from the `toCommand` step, since
  `EntitySync.apply` always leaves it at `0`.
- **Confirmed, not just assumed: a CSV line that fails `CountryCsvParser.parse`'s own regex never
  reaches `toCommand`, so it can never contribute to `skippedInvalid` as implemented.** `CountrySync`'s
  `skippedInvalid` only counts `toCommand`-stage (post-parse) validation failures. In practice, for
  Country specifically, this branch is close to unreachable through the real pipeline: the parse
  regex's `[A-Za-z]{2}` requirement already enforces everything `CountryCode.validateAll` checks
  (non-blank, exact length 2, letters only), so any row that survives `parse` will always pass
  `toCommand`. This mirrors the same "only certain rules are reachable at certain layers" nuance
  `CLAUDE.md` already documents for the HTTP path. `CountrySyncSpec`'s malformed-line test verifies the
  actually-true behavior — the sync tolerates the bad line without aborting — rather than asserting a
  `skippedInvalid` count that can't occur here.
- **`Main`'s layer composition copies `WiringModule.scala`'s existing pattern exactly**: build the
  Country repo layer once (`QuillDataSourceLayer.live >>> QuillCountryRepository.layer`), reuse it via
  `>>>` per service, combine the four resulting layers with `++`. `QuillDataSourceLayer.live` reads
  `POSTGRES_URL`/`POSTGRES_USER`/`POSTGRES_PASSWORD` from `sys.env` with the same defaults as
  `docker-compose.yml` — no config file needed.
- **`master-data-sync`'s `build.sbt` block now `.dependsOn(domain, application, persistenceQuill)`** —
  no new `libraryDependencies` were needed; Quill/Postgres JDBC/HikariCP all arrive transitively through
  `persistenceQuill`, the same convention already used by `integrationTests`/`bootstrap`.
- **`CountrySyncSpec` uses `Ref`-backed stub use cases, not a real DB** — `CreateCountryUseCase`/
  `UpdateCountryUseCase`/`DeleteCountryUseCase` are single-abstract-method traits (same precedent as
  `CountryEndpointsSpec`'s `defaultCreate`), so each is a lambda closing over one shared
  `Ref[List[Country]]`; `FindCountryUseCase` needs a full anonymous implementation (3 unused methods
  die, matching `CountryRepositoryStub`'s convention, plus `findAllUnbounded = state.get`). A temp CSV
  file is written via `TempDirectory.create` + `zio-nio`'s `Files.writeLines`, same as
  `CountryCsvParserSpec`.
- **Known, still-not-fixed gap, unchanged from the previous increment**: `QuillCountryRepository.delete`
  doesn't catch FK violations (`.orDie`s instead), so a future Airport/Airline sync that deletes a
  Country still referenced by one would crash the fiber rather than being caught by `EntitySync.apply`'s
  `.foldZIO` (which only catches typed failures, not defects). Not a risk for Country alone — nothing
  in the current schema FKs to `countries` from a row this tool itself writes yet.

## Not in this slice

`AirportSync`/`AirlineSync`, Airport/Airline CSV parsing, `Main`'s CLI arg parsing (`--entity=`), and a
`--dry-run` flag are all still open per §9. The `QuillCountryRepository.delete` FK-violation gap is
noted but not fixed.

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountrySync.scala`
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountrySyncSpec.scala`
  — 4 tests: create-only, update-only, delete-only, and a malformed CSV line tolerated without
  aborting (see the `skippedInvalid` note above for why that test doesn't assert a nonzero count).

**Edited:**
- `domain/src/main/scala/dev/cmartin/aerohex/domain/country/CountryRepository.scala` — `findAllUnbounded`.
- `domain/src/main/scala/dev/cmartin/aerohex/domain/country/FindCountryUseCase.scala` — `findAllUnbounded`.
- `infrastructure/persistence-quill/.../country/QuillCountryRepository.scala` — real implementation.
- `infrastructure/persistence-postgres/.../country/DoobieCountryRepository.scala` — kept schema-consistent.
- `application/src/main/scala/dev/cmartin/aerohex/application/country/FindCountryService.scala` — delegates.
- `application/src/test/scala/dev/cmartin/aerohex/application/country/CountryRepositoryStub.scala` — fixture update.
- `adapter-http/src/test/scala/dev/cmartin/aerohex/adapter/http/country/CountryEndpointsSpec.scala` — fixture update.
- `infrastructure/master-data-sync/build.sbt` (project block) — `.dependsOn(domain, application, persistenceQuill)`.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/Main.scala` — real layer wiring + `CountrySync.sync` call.
- `docs/todo/master-data/analysis.md` — status header, §3.2, §5.1, §7.1, §9.

## Verification

1. `sbt scalafmtAll` then `sbt compile` (whole build) — zero errors, zero warnings.
2. `sbt "testOnly *"` at root — 343 tests, unchanged (no new tests outside `master-data-sync`; the two
   trait-implementer fixtures were mechanical updates, not new test cases).
3. `sbt masterDataSync/compile` then `sbt masterDataSync/test` — 22 tests green (18 existing + 4 new
   `CountrySyncSpec`).
4. **Real end-to-end run against local Postgres** (`docker compose up -d postgres`; schema present, 1
   row `ES, Spain` confirmed via `docker exec ... psql` beforehand): `sbt masterDataSync/run` (with
   `JAVA_HOME` pinned to a Java 21 install, per `CLAUDE.md`). Logged
   `created: 248, updated: 0, deleted: 0, unchanged: 1, skippedInvalid: 0, skippedConflict: 0`.
   Confirmed via `psql`: row count `1` → `249`; all 4 quoted-comma edge cases present with correct
   names. A second `sbt masterDataSync/run` logged `created: 0, updated: 0, deleted: 0, unchanged: 249`
   — idempotent, zero writes.
