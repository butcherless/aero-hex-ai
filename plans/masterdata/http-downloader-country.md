# Implement HttpDownloader for master-data-sync (Country source only)

> **Status:** Implemented.

## Goal

`docs/todo/master-data/analysis.md` sketched an `HttpDownloader` component for the future
master-data sync pipeline but never verified its exact API against current `zio-http` docs, nor
compared it against alternatives the way §4.3 did for the temp-directory choice
(`plans/masterdata/master-data-sync-scaffold.md`). This change repeats that process for the HTTP download
step — research ZIO's own answer first, compare against the plain-JDK baseline and other libraries,
record the comparison in the analysis doc (new §4.4), then build the component and its tests.

**Scope:** the Country source only
(`https://datahub.io/core/country-list/_r/-/data.csv`, which redirects — a real reason to need
redirect-following, not a hypothetical one). Airport/Airline downloads and CSV parsing/reconciliation
are still later increments, continuing the incremental-slice pattern from
`plans/masterdata/master-data-sync-scaffold.md`. `HttpDownloader` was built and verified standalone first (unit
tests + a manual real-network check); `Main.scala` was then updated in the same change to actually
call it — see "Main wiring" below — so `sbt masterDataSync/run` now performs a real download, not
just the temp-dir smoke test the previous slice left it at.

## Decisions

- **`zio-http` `Client`, not a new dependency.** Already used server-side in `adapter-http`; adding
  it to `master-data-sync` pulls in no new artifact for the whole build, unlike `zio-nio` was.
  Rejected alternatives (`java.net.http.HttpClient`, `sttp-client4`, Apache HttpClient/OkHttp):
  analysis doc §4.4/§10 — not repeated here.
- **Redirect-following via `ZClientAspect.followRedirects`, composed with `ZIO#updateService`, not
  a fetched-and-transformed `client` value.** Calling the `ZClient` instance's own `.request(...)`
  directly is deprecated since `zio-http` 3.0.0 and produces a compiler warning; `updateService`
  transforms the ambient `Client` service so the non-deprecated `Client.streaming(...)` companion
  sugar (which does the same `ZIO.service[Client]` lookup internally) picks up the aspect.
- **Non-2xx responses fail explicitly**, checked via `Status.isSuccess`, so an error page's body
  never gets written to disk as if it were the real payload.
- **Logging**: `ZIO.logInfo` before starting and on success (with a human-readable size —
  `humanReadableSize`, since Airport's real source file is "tens of MB" and raw byte counts don't
  read well at that scale), `ZIO.logError` via `.tapError` on any failure (covers both the explicit
  non-2xx case and genuine network/IO errors uniformly, in one place). Row/record counts are
  deliberately not logged here — they're format-specific (CSV vs. `.dat`) and belong to whichever
  parser reads the file next; `HttpDownloader` only ever sees an undifferentiated byte stream.
- **Test server: plain `zio.http.Server` bound to port 0, not `zio-http-testkit`.** No precedent in
  this codebase for testing an HTTP client; `zio-http-testkit`'s `TestServer` would reduce
  boilerplate but its latest Maven Central release (`3.3.3`) lags this project's `zio-http`
  (`3.11.3`) — a real version gap the core artifact doesn't have. Routes are installed once when the
  suite's shared layer builds (`Server.install(routes)` inside a `ZLayer`), not per test — the first
  version of this spec called `Server.install` in each test against the one shared server instance,
  which registered the same routes three times and logged "duplicate routes detected" warnings on
  every run after the first.

## Main wiring

`Main.run` now: creates the temp dir (unchanged from the previous slice) → downloads the Country CSV
into it via `HttpDownloader.download(countryUrl, dir / "countries.csv").provide(Client.default)` →
logs completion → deletes the temp dir (`ZIO.acquireRelease`'s release action, unchanged). No CSV
parsing yet — the downloaded file is inspected only by its log line (size) before the temp dir is
deleted; parsing/reconciliation is still a later increment. `countryUrl` is a `private val` constant
in `Main`, the same literal already used in `HttpDownloaderSpec`'s manual-check precedent and
recorded in the analysis doc's §2.1.

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/HttpDownloader.scala`
  — `download(url: String, destFile: Path): ZIO[Client, Throwable, Path]`.
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/HttpDownloaderSpec.scala`
  — `ZIOSpecDefault`, three tests: direct 200 download, redirect-following to the same content, and
  a 404 failure case. Reuses `TempDirectory.create`/`delete` (from
  `plans/masterdata/master-data-sync-scaffold.md`) for a scratch destination directory per test.

**Edited:**
- `build.sbt` — added `zioHttp`, `zioStreams` to `masterDataSync`'s `libraryDependencies` (both
  already existed as `val`s in `project/Dependencies.scala`; no `Versions.scala` change needed,
  unlike the `zio-nio` increment).
- `docs/todo/master-data/analysis.md` — new §4.4 (comparison above); updated §5.1's `HttpDownloader`
  row (the original sketch's `download(url: URL, destDir: Path)` became
  `download(url: String, destFile: Path)` once actually built); updated §9's open-decisions table
  with an `HTTP download client` row; updated §10 with the rejected-alternatives summary. Also fixed
  a stale reference to `Files.createTempDirectoryScoped` in §9's `Temp directory library` row, left
  over from before the temp-dir slice's implementation settled on two explicit functions instead.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/Main.scala`
  — see "Main wiring" above.

## Verification

1. `sbt scalafmtAll` then `sbt masterDataSync/compile` — zero errors, zero warnings. Caught two real
   API mismatches against the initial draft: `ZClientAspect.followRedirects`'s callback must return
   the `Response` (not `Unit`), and `zio.nio.file.Path.javaPath` is `private[nio]` — fixed by
   switching to `ZSink.fromFile` + the public `.toFile` accessor.
2. `sbt masterDataSync/test` — all 5 tests green (2 `TempDirectorySpec` + 3 `HttpDownloaderSpec`).
3. `sbt compile "testOnly *"` at root — 343 tests, unchanged (`masterDataSync` stays outside root's
   aggregate).
4. `sbt bloopInstall` — regenerated `.bloop/masterDataSync{,-test}.json` for the new dependencies.
5. Manual, real-network check (via `sbt masterDataSync/console`, before `Main` was wired):
   downloaded the live Country CSV from `datahub.io`, confirming `followRedirects` actually follows
   that source's real `302` (not just the local-server simulation) — 4,048 bytes, content matched
   the expected `Name,Code` header and country rows.
6. `sbt masterDataSync/run` (after wiring `Main`) — real end-to-end run against the live source:
   creates the temp dir, downloads 4.0 KB from `datahub.io` (redirect followed), logs completion,
   deletes the temp dir. Reran the full verification (`compile`, `test`, root `testOnly *`,
   `bloopInstall`) after the `Main` change — all still clean.
