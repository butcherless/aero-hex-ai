# Implement CountryCsvParser for master-data-sync

> **Status:** Implemented — `parse` and `toCommand` both.

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

**Scope:** Country only. Originally `parse` only (returns raw, unvalidated rows —
`CountryRow(name, code)`); `toCommand` (mapping a row to a domain `CreateCountryCommand`) was added
in a follow-up pass — see "`toCommand` addendum" below — once the user asked to move forward with
it specifically. Actually invoking `CreateCountryService` to persist a built command is still a
later increment; this module only builds commands so far, it doesn't call anything to save them.

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

## `toCommand` addendum

Unlike `parse` (and unlike `TempDirectory`/`HttpDownloader` before it), `toCommand` wasn't a
new-library decision — the validation mechanism (ZIO Prelude `Newtype`, `CountryCode.validateAll`)
is already this project's fixed, mandated create-path pattern (`CLAUDE.md`'s "Key patterns"
section). The work here was reading the *existing* pattern precisely and mirroring it, not comparing
alternatives:

- **Mirrors `CreateCountryRequest.toCommand` exactly** (`adapter-http/.../country/CountryDto.scala`):
  `CountryCode.validateAll(row.code)` (accumulating validation — not `.make`, which also exists,
  inherited from `Newtype`, but only does single fail-fast; the real create-path convention uses
  `.validateAll`), folded into `DomainError.InvalidCountryCode` via `.toEitherWith` + `ZIO.fromEither`.
- **`IO[DomainError, CreateCountryCommand]`, not the sketch's `Either[String, Command]`** — settled
  once actually built, to match the real existing `toCommand` convention rather than invent a new
  error shape.
- **First `.dependsOn(...)` this module has ever had.** `CreateCountryCommand` and `DomainError` both
  live in `domain`, not `application` — `master-data-sync` now depends on `domain` only.
  `application`/`persistenceQuill` still aren't needed, since `toCommand` only *builds* a command; it
  doesn't call `CreateCountryService` to persist one. No new `libraryDependencies` — `zioPrelude`
  arrives transitively through `domain`, the same way `application`'s own `build.sbt` block never
  redeclares it either.
- **Not re-testing `CountryCode.validateAll`'s own rule set** — that's already exhaustively covered
  in `domain`'s own `CountryCodeSpec` per `CLAUDE.md`. The two new tests here only check `toCommand`'s
  own wiring: a well-formed row builds the expected command; a malformed one fails with
  `DomainError.InvalidCountryCode` carrying exactly one error.

## Files touched

**New (original `parse` pass):**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountryCsvParser.scala`
  — `CountryRow(name, code)` + `parse(file: Path): IO[IOException, List[CountryRow]]`.
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/CountryCsvParserSpec.scala`
  — `ZIOSpecDefault`, two tests: valid rows parse correctly (header dropped, quoted-comma edge case
  preserved) and a malformed line is skipped without aborting the rest of the file. Real file I/O
  against a `TempDirectory`-created scratch dir, fixture written via `zio-nio`'s `Files.writeLines`.

**Edited (`toCommand` addendum pass):**
- `CountryCsvParser.scala` — added `toCommand(row: CountryRow): IO[DomainError, CreateCountryCommand]`.
- `CountryCsvParserSpec.scala` — two new tests (above).
- `build.sbt` — added `.dependsOn(domain)` to the `masterDataSync` project block.

**Edited (both passes):**
- `docs/todo/master-data/analysis.md` — new §4.5 (`parse`'s file-line-reading comparison); §3.2's
  file layout corrected to flat (dropping the `downloader/`/`parser/`/`sync/` subpackages the
  original sketch had, which `HttpDownloader` had already silently deviated from); §5.1's
  `CountryCsvParser` row updated for both functions; §9/§10 get the `parse`-side comparison
  summaries; §3.1's dependency note updated now that `.dependsOn(domain)` is real.
- `CLAUDE.md` — (`parse` pass only) added an explicit rule to "Documentation sources": when a new
  capability needs a library, check ZIO core/ecosystem first, then the Scala/JDK core baseline, then
  other alternatives — codifying the pattern this file and the previous two `plans/*.md` records
  already established in practice, at the user's request.

## Verification

1. `sbt scalafmtAll` then `sbt masterDataSync/compile` — zero errors, zero warnings both passes; the
   `toCommand` pass specifically confirmed the new `.dependsOn(domain)` project (not just library)
   dependency resolves cleanly.
2. `sbt masterDataSync/test` — all 11 tests green (2 `TempDirectorySpec` + 5 `HttpDownloaderSpec` + 4
   `CountryCsvParserSpec`), including the expected `WARN` log for the deliberately malformed `parse`
   test line.
3. `sbt compile "testOnly *"` at root — 343 tests, unchanged both passes (`masterDataSync` stays
   outside root's aggregate; `domain` itself, already in the aggregate, was untouched).
4. `sbt bloopInstall` — regenerated `.bloop/masterDataSync{,-test}.json` both passes.
5. Manual, real-data checks (via `sbt masterDataSync/console`): `parse` pass — downloaded the live
   Country CSV, all 249 rows parsed, all 4 quoted-comma edge cases (Bonaire, Palestine, Saint Helena,
   Tanzania) round-tripped correctly, zero malformed-line warnings. `toCommand` pass — ran `toCommand`
   over all 249 real parsed rows: 249 successes, 0 failures, sample output confirmed correct
   `CreateCountryCommand` values (e.g. `CreateCountryCommand(AF, Afghanistan)`).
