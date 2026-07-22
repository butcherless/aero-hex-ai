# Implement EntitySync: generic reconciliation algorithm

> **Status:** Implemented — `loadExisting`/`reconcile`/`apply` + `SyncReport`, unit-tested standalone.

## Goal

`docs/todo/master-data/analysis.md` §5.1/§7 sketched `EntitySync` as the generic, non-entity-specific
reconcile-and-apply algorithm sitting between each entity's parsed source rows and its existing
`Create`/`Update`/`Delete` use cases — but its exact signatures were never pinned down against a real
type instantiation. This change builds and verifies it standalone, following the same incremental-slice
pattern as `plans/masterdata/master-data-sync-scaffold.md`, `plans/masterdata/http-downloader-country.md`, and
`plans/masterdata/country-csv-parser.md`: build the component, test it in isolation, defer wiring it into
`Main`/`CountrySync` to a later increment.

**Scope:** `EntitySync.scala` + `SyncReport.scala` + `EntitySyncSpec.scala` only. No `CountrySync.scala`,
no `Main` wiring, no new `build.sbt` dependencies or `.dependsOn(...)` — `domain` (already a dependency,
for `DomainError`) and ZIO core (already a dependency) are all this increment needs.

## Decisions

- **Fully generic over `K`/`E`, no entity-specific code.** `loadExisting[K, E]`/`reconcile[K, E]`/
  `apply[K, E]` — the doc's original sketch (`UIO[Map[NaturalKey, Entity]]`, `SyncPlan`, no type
  parameters shown) is now pinned to real signatures once actually built, the same way
  `HttpDownloader`/`CountryCsvParser`'s signatures were settled once built rather than kept at the
  sketch stage.
- **`SyncPlan[K, E]` is a top-level case class in `EntitySync.scala`**, not nested in the companion
  object — mirrors `CountryRow` living alongside `CountryCsvParser` in the same file, this module's
  existing precedent for a small data type paired one-to-one with its algorithm.
- **Equality for "is this row different" is plain `==`** (case-class structural equality), not a
  custom typeclass — matches §9's "field-by-field equality on the mapped subset only", since for
  `Country` the case class already contains exactly the mapped fields (`code`, `name`). Revisit if a
  future entity's case class carries fields the source doesn't supply.
- **`apply` never fails the fiber.** Every `create`/`update`/`delete` call is run through a private
  `runLogged` helper (`.foldZIO(error => ZIO.logWarning(...).as(false), _ => ZIO.succeed(true))`); a
  failure is logged at `WARN` with the row/key and the `DomainError`, then counted into
  `skippedConflict` rather than propagated — this is also the generic mechanism behind §7.2's "catch
  FK violation per-row, log, continue". The FK-violation-to-`DomainError` mapping gap in
  `QuillCountryRepository` (confirmed in §7.2 — the persistence layer needs to map
  `sqlstate.class23.FOREIGN_KEY_VIOLATION` to a `DomainError` for this to actually trigger on a real
  dangling reference) is a separate, **known, not-in-scope gap** — `EntitySync.apply`'s catch-and-log
  already handles it generically the moment that mapping exists; nothing in this component needs to
  change when it's fixed.
- **`DomainError` hardcoded as the create/update/delete error channel**, not generalized further —
  every existing use case in this codebase already fixes on `IO[DomainError, _]` (`CreateCountryService`,
  `UpdateCountryService`, `DeleteCountryService`), so a further type parameter would add complexity with
  no real caller needing anything else.
- **`SyncReport` only populates `created`/`updated`/`deleted`/`unchanged`/`skippedConflict` this
  increment; `skippedInvalid` stays `0`.** That counter belongs to the parse/`toCommand` stage
  (`CountryCsvParser.parse`/`.toCommand`'s logged-and-skipped rows, §8) — summed in with this report
  only once `CountrySync`/`Main` combines both sources together, a later increment.
- **Test spec exercises the generic functions against real `Country`/`CountryCode`** as the concrete
  `E`/`K` instantiation, not toy stand-ins — follows `CountryCsvParserSpec`'s existing precedent in
  this module of testing against real domain types.

## Not in this slice

`CountrySync.scala` (the concrete `keyOf`/`create`/`update`/`delete` wiring for Country, plus the
`FindCountryUseCase.findAllUnbounded`/`CountryRepository.findAllUnbounded` addition §7.1 flags as
needed), `AirportSync.scala`/`AirlineSync.scala`, and any `Main` wiring are all still open — this
increment only builds and verifies the generic algorithm standalone. The `application`/
`persistenceQuill` module dependencies `CountrySync` will need are not added yet.

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/EntitySync.scala`
  — `SyncPlan[K, E]` + `EntitySync.loadExisting`/`.reconcile`/`.apply`.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/SyncReport.scala`
  — `SyncReport(created, updated, deleted, unchanged, skippedInvalid, skippedConflict)` + `.log(): UIO[Unit]`.
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/EntitySyncSpec.scala`
  — `ZIOSpecDefault`, 7 tests: `loadExisting` keys by natural key; `reconcile` create-only, update-only,
  delete-only, and unchanged-stays-unchanged; `apply` happy path counts; `apply` catches a fully-failing
  create/update/delete and counts every one as `skippedConflict` without failing the suite.

**Edited:**
- `docs/todo/master-data/analysis.md` — status header; §3.2 marks `EntitySync.scala`/`SyncReport.scala`
  implemented; §5.1's four rows updated with real signatures; §9's "different comparison" row marked
  decided/implemented, plus a new row for the generic algorithm itself (also notes the
  `QuillCountryRepository` FK-mapping gap as a still-open, separate problem).

## Verification

1. `sbt scalafmtAll` then `sbt masterDataSync/compile` — zero errors, zero warnings. (New files must
   be `git add`ed first — `.scalafmt.conf`'s `project.git = true` only formats tracked files.)
2. `sbt masterDataSync/test` — all 18 tests green (2 `TempDirectorySpec` + 5 `HttpDownloaderSpec` +
   4 `CountryCsvParserSpec` + 7 `EntitySyncSpec`), including the expected `WARN` logs from the
   deliberately-failing `apply` test.
3. `sbt compile "testOnly *"` at root — 343 tests, unchanged (`masterDataSync` stays outside root's
   aggregate; no other module touched).
4. `sbt bloopInstall` — not strictly required this increment (no new dependency, no `build.sbt`
   change), but safe/idempotent to run to confirm no drift.
5. No manual/real-network check needed — `EntitySync` is pure/stub-driven by design this increment,
   unlike `HttpDownloader`/`CountryCsvParser`'s live-source checks.
