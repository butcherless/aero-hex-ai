# Scaffold the master-data-sync module: Main + temp-dir lifecycle

> **Status:** Implemented (first slice only — see "Not in this slice" below).

## Goal

`docs/todo/master-data/analysis.md` is a completed design doc for a future master-data sync tool
that downloads Country/Airport/Airline reference data, reconciles it against Postgres, and cleans
up after itself. This change implements the **first slice** of that design: the module itself, a
`Main` entry point, and two standalone ZIO functions to create/delete the temp directory — enough
to compile, test, and run end-to-end before the download/parse/reconcile pipeline (still an open,
much bigger piece of the analysis doc) gets built on top of it in later increments.

## Decisions

- **Temp-directory library: `zio-nio` 2.0.2.** Already decided and fully compared against a
  hand-rolled JDK wrapper, `better-files`, `os-lib`, and `scala.reflect.io.Directory` in the
  analysis doc's §4.3 — not repeated here. This change is the first real consumer of that decision.
- **No `ZLayer` for `TempDirectory`** — it's two pure, stateless functions (`create`/`delete`), not
  a stateful service. Follows the precedent of
  `domain/src/main/scala/dev/cmartin/aerohex/domain/route/RouteValidator.scala`: a plain `object`
  with top-level `def`s, no companion layer — the shape this codebase already uses for pure
  helpers, distinct from the `val layer` convention used by stateful infrastructure classes.
- **Two explicit functions, composed with `ZIO.acquireRelease` in `Main`**, rather than zio-nio's
  single opaque `createTempDirectoryScoped`. This satisfies "functions to create and delete" as two
  independently testable units, while `Main` still gets guaranteed cleanup (the release action runs
  even if something between create and delete fails) — the shape later increments' real
  download/parse logic will need once it sits between the two calls.
- **Module has zero `.dependsOn(...)` in this slice.** `Main`/`TempDirectory` only need
  `zio`/`zio-nio`/logging, not `domain`/`application`/`persistenceQuill`. Those get added in the
  increment that wires real repository calls — keeps this slice's dependencies truthful to what it
  actually does today rather than pre-declaring unused ones.
- **Not added to root's `.aggregate(...)` / `coverageProjects`.** Same rationale already recorded
  in the analysis doc (§3.1): a cron-triggered, externally-invoked lifecycle, not "always
  compiled/tested with the HTTP server." Matches `integrationTests`' treatment — invoke directly
  with `sbt masterDataSync/compile` / `sbt masterDataSync/test` / `sbt masterDataSync/run`.
- **`sbt-assembly` stays enabled** (no `.disablePlugins(AssemblyPlugin)`) — matches `bootstrap`,
  since this module will need a runnable fat jar for eventual OS-cron invocation.

## Not in this slice

Everything else in the analysis doc's design is still open: CSV/`.dat` parsing, reconciliation
against Postgres, CLI `--entity=` argument handling, dry-run mode, and the
`domain`/`application`/`persistenceQuill` dependencies those need. `Main.run` at the time of this
slice only exercised the temp-dir lifecycle as a smoke test — HTTP download (Country source only)
was added next, in `plans/http-downloader-country.md`, which also wired `Main` to call it.

## Files touched

**New:**
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/TempDirectory.scala`
  — `create(prefix): IO[IOException, Path]` / `delete(dir): IO[IOException, Unit]`, both thin
  wrappers over `zio.nio.file.Files.createTempDirectory` / `.deleteRecursive`.
- `infrastructure/master-data-sync/src/main/scala/dev/cmartin/aerohex/infrastructure/masterdata/Main.scala`
  — `ZIOAppDefault`; `Runtime.removeDefaultLoggers >>> SLF4J.slf4j` bootstrap layer, matching
  `bootstrap/src/main/scala/dev/cmartin/aerohex/bootstrap/Main.scala`'s pattern; composes
  `TempDirectory.create`/`delete` via `ZIO.acquireRelease`.
- `infrastructure/master-data-sync/src/main/resources/logback.xml` — same shape as
  `bootstrap/src/main/resources/logback.xml`, own log file (`/tmp/master-data-sync.log`) so a
  concurrent run never collides with `bootstrap`'s.
- `infrastructure/master-data-sync/src/test/scala/dev/cmartin/aerohex/infrastructure/masterdata/TempDirectorySpec.scala`
  — `ZIOSpecDefault`, two tests: `create` makes a real directory, `delete` removes it *including a
  file inside it* (the second test specifically exercises the recursive-delete gap the analysis
  doc's §4.3 flagged in the plain-JDK alternative — `Files.delete` only removes empty directories).

**Edited:**
- `project/Versions.scala` — `val zioNio = "2.0.2"`.
- `project/Dependencies.scala` — `val zioNio = "dev.zio" %% "zio-nio" % Versions.zioNio`.
- `build.sbt` — new `lazy val masterDataSync` project block (after `integrationTests`).
- `CLAUDE.md` — `master-data-sync` entry in the "Module dependency graph" ASCII diagram; `ZIO NIO`
  row in the "Tech stack" table, scoped as `master-data-sync only` (same pattern already used for
  `Testcontainers`, which is likewise only an `integration-tests`-scoped dependency).

## Verification

1. `sbt scalafmtAll` then `sbt masterDataSync/compile` — zero errors, zero warnings. (New files
   must be `git add`ed first — `.scalafmt.conf`'s `project.git = true` only formats tracked files,
   same gotcha `CLAUDE.md`'s Formatter section already documents.)
2. `sbt masterDataSync/test` — both `TempDirectorySpec` tests green.
3. `sbt compile "testOnly *"` at root — confirms the new module's exclusion from `root`'s
   aggregate didn't regress anything else (343 tests, unchanged).
4. `sbt bloopInstall` — regenerates `.bloop/`, including the new `masterDataSync`/`masterDataSync-test`
   configs.
5. Manual run: `sbt masterDataSync/run` — three log lines observed ("Created temporary
   directory: ...", the placeholder completion message, "Deleted temporary directory: ..."); the
   printed path confirmed gone via `ls` immediately after the run exits.
