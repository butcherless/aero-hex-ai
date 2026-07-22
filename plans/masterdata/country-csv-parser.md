# Implement CountryCsvParser for master-data-sync

> **Status:** Implemented.

## Goal

`docs/todo/master-data/analysis.md` §5.1 sketched a `CountryCsvParser.parse` component: skip the
header line, match every remaining line against §2.1's regex
(`^(?:"([^"]+)"|([^,]+)),([A-Za-z]{2})$`), tolerate a non-matching line as a logged-and-skipped
parse error (§8) rather than aborting the file. The regex itself — and the decision *not* to pull in
`scala-csv` for Country's simple two-column shape — was already settled in §2.1/§4.2; what wasn't
verified was the file-reading mechanism underneath it. This change applies the same research process
used for `TempDirectory` (`plans/masterdata/master-data-sync-scaffold.md`) and `HttpDownloader`
(`plans/masterdata/http-downloader-country.md`): ZIO's own answer first, then the plain-Scala/JDK baseline, then
alternatives (analysis doc §4.5) — then builds the component and its tests.

**Scope:** `parse` only, Country only — returns raw, unvalidated rows (`CountryRow(name, code)`).
Mapping a row to a domain `Country`/`CreateCountryCommand` (the sketch's separate `toCommand`
function) needs `CountryCode.make` from the `domain` module, which this module still deliberately
has zero `.dependsOn(...)` for — a later increment, same incremental-slice pattern as the last two.

## Decisions

- **`zio-nio`'s `Files.readAllLines`, no new dependency.** Already added to this module for
  `TempDirectory`. Full comparison against `scala.io.Source`, plain JDK `Files.readAllLines`, and
  `zio-streams`' line-streaming: analysis doc §4.5 — not repeated here. Eager/in-memory is
  appropriate for Country's small (~4 KB) file; the streaming alternative is deferred to whenever the
  much-larger Airport parser is built.
- **`IO[IOException, List[CountryRow]]`, not `Task[List[SourceRow]]`.** `Files.readAllLines`'s error
  is precisely `IOException` — kept narrow rather than widened to `Throwable`, matching
  `TempDirectory`'s precedent. `CountryRow` instead of a shared generic `SourceRow`: Airport/Airline's
  row shapes aren't designed yet, so generalizing now would guess at an unknown future shape.
- **File placed flat in the `masterdata` package root**, not under a `parser/` subpackage as
  originally sketched in the analysis doc's §3.2 — matching what `TempDirectory`/`HttpDownloader`
  already did in the previous two increments (a deviation from the original sketch that was never
  updated in the doc until now). §3.2 corrected to match.
- **Header dropped unconditionally by position** (`lines.drop(1)`), never logged as a parse error —
  matches §2.1's "skipped explicitly by line number... an expected line, not a data problem". Every
  other non-matching line is logged at `WARN` with the raw content and skipped, never aborting the
  rest of the file (§8).

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountryCsvParser.scala`
  — `CountryRow(name, code)` + `parse(file: Path): IO[IOException, List[CountryRow]]`.
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountryCsvParserSpec.scala`
  — `ZIOSpecDefault`, two tests: valid rows parse correctly (header dropped, quoted-comma edge case
  preserved) and a malformed line is skipped without aborting the rest of the file. Real file I/O
  against a `TempDirectory`-created scratch dir, fixture written via `zio-nio`'s `Files.writeLines`.

**Edited:**
- `docs/todo/master-data/analysis.md` — new §4.5 (comparison above); §3.2's file layout corrected to
  flat (dropping the `downloader/`/`parser/`/`sync/` subpackages the original sketch had, which
  `HttpDownloader` had already silently deviated from); §5.1's `CountryCsvParser` row updated
  (return type + `CountryRow` vs `SourceRow`); §9's open-decisions table gets a `File line reading`
  row; §10 gets the rejected-alternatives summary.
- `CLAUDE.md` — added an explicit rule to "Documentation sources": when a new capability needs a
  library, check ZIO core/ecosystem first, then the Scala/JDK core baseline, then other
  alternatives — codifying the pattern this file and the previous two `plans/*.md` records already
  established in practice, at the user's request.

## Verification

1. `sbt scalafmtAll` then `sbt masterDataSync/compile` — zero errors, zero warnings.
2. `sbt masterDataSync/test` — all 7 tests green (2 `TempDirectorySpec` + 3 `HttpDownloaderSpec` + 2
   `CountryCsvParserSpec`), including the expected `WARN` log for the deliberately malformed test
   line.
3. `sbt compile "testOnly *"` at root — 343 tests, unchanged (`masterDataSync` stays outside root's
   aggregate).
4. `sbt bloopInstall` — regenerated `.bloop/masterDataSync{,-test}.json`.
5. Manual, real-data check (via `sbt masterDataSync/console`): downloaded the live Country CSV, then
   ran `CountryCsvParser.parse` on it — all 249 rows parsed, first/last rows correct, and all 4 rows
   with a comma in the name (Bonaire, Palestine, Saint Helena, Tanzania — the exact cases §2.1
   documents) round-tripped correctly through the quoted-field regex branch, with zero
   malformed-line warnings against the real data.
