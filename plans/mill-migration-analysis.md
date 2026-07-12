# Plan: Mill build tool migration — feasibility analysis

## Goal

Evaluate whether this project should move from sbt (currently 1.12.13, after the
2026-07-12 revert from sbt 2.0.1 — see git history around that revert) to the Mill
build tool, replicating everything the current sbt build does. This document is
**analysis only** — no build files were written or modified to produce it, and no
migration has been implemented. It exists so the conclusion and the reasoning behind
it survive past the conversation that produced them.

## Conclusion / Recommendation

**Not recommended right now.** Treat this as a "watch, don't migrate" item; revisit in
6–12 months.

The mechanical parts of this build (11-module dependency graph, per-module
`dependsOn`, Scala 3.3.8, zio-test) translate to Mill cleanly and would likely take
well under a day. Two plugin-shaped pieces carry real risk that outweighs the benefit
for a project this size:

- **Scoverage on Scala 3 via `mill-contrib-scoverage`** is actively maintained (recent
  PRs bump it to scoverage 2.5.x, and a past "Fix Scoverage report generation in
  Scala 3" fix landed), but this project's specific requirement — relocate the
  coverage **data directory** outside the build tool's ephemeral output tree so a
  `clean` never wipes the statement catalog — has no documented equivalent. Mill's
  contrib module writes to `out/<module>/scoverage/data/dest`, i.e. inside Mill's own
  cache (`out/`), which `mill clean` removes — exactly the failure mode this
  project's sbt build deliberately engineered around
  (`coverageDataDir := baseDirectory.value / ".coverage-data"`, see CLAUDE.md's
  Coverage section). This would need to be proven out in a spike, not assumed.
- **The hand-written `assemblyMergeStrategy`** (in `build.sbt`'s `bootstrap` project)
  is actually more portable to Mill than it first appears — Mill's default assembly
  behavior (silent first-wins for anything without an explicit rule) already
  reproduces the "first" cases (`org/jline`, `scala/tools`, `io/getquill`,
  `compiler.properties`) for free. But Mill's rule matching is regex/exact-string
  based rather than segment-based `PathList`, and rule precedence depends on list
  *order* (`Seq[Rule]`, first pattern match wins) rather than pattern-match
  fallthrough — the translation is mechanical but must be built and tested
  carefully, not copy-pasted.

Neither of these is a hard blocker, but both require hands-on validation against
*this* build's actual jars (Quill/Doobie/Testcontainers/Tapir/zio-kafka all bring in
the exact packages the merge strategy was written to fix) before trusting the
result. Given the project currently works fine on sbt 1.12.13 (which is why it was
just reverted from sbt 2.0.1), and Mill's main advertised wins (faster incremental
builds, simpler BSP/IDE story) are "nice to have" rather than blocking anything, the
effort-to-benefit ratio doesn't justify it today. The strongest genuine win —
dropping `sbt-bloop` entirely in favor of Mill's built-in BSP server — is real but
not, by itself, worth a full build-tool migration.

If this is pursued anyway, the recommended approach is a **spike branch**: port just
`shared-kernel` + `domain` + one infra module (e.g. `persistence-quill`, since it has
the zio-json version-scheme conflict) + `bootstrap` (the assembly), get scoverage +
assembly + zio-test all green on that subset first, *then* decide whether to do the
rest.

## Feature-by-feature mapping

| # | sbt 1.12.13 feature | Mill equivalent | Status |
|---|---|---|---|
| 1 | 11 subprojects, `dependsOn` graph, `integration-tests` excluded from root aggregate | `moduleDeps`; module tree mirrors the existing `infrastructure/*` folder layout automatically (no `moduleDir` overrides needed) | Direct equivalent, low risk |
| 2 | Scala 3.3.8, `scalacOptions` incl. `-Wunused:all` etc. | `def scalaVersion`, `def scalacOptions` per module or in a shared trait | Direct equivalent |
| 3 | `testFrameworks += ZTestFramework` (zio-test) | `TestModule.ZioTest` trait (native support, added via mill PR #2432) | Direct equivalent |
| 4 | sbt-assembly 2.3.1, hand-written `assemblyMergeStrategy` | Built-in `assemblyRules: Seq[Assembly.Rule]` (`Append`, `AppendPattern`, `Exclude`, `ExcludePattern`, `Relocate`); default rules already do "first-wins" for unmatched paths | Mostly expressible — needs rule reordering, see Issues #2 |
| 5 | sbt-scalafmt 2.6.1, `scalafmtAll`/`scalafmtCheckAll` | `ScalafmtModule` trait: `reformat`/`checkFormat` per module, `__.sources` for all | Direct equivalent; `.scalafmt.conf` unchanged |
| 6 | sbt-scoverage 2.4.4, relocated `coverageDataDir`, `coverageAggregate` | `mill.contrib.scoverage.ScoverageModule` + a root `ScoverageReport` module (`htmlReportAll`, `xmlReportAll`, `xmlCoberturaReportAll`) | Aggregate workflow supported; **data-dir relocation outside `out/` is undocumented/unverified** — see Issues #1 |
| 7 | sbt-bloop 2.1.1 (`bloopInstall`) | Not needed — Mill has a native BSP server (`mill mill.bsp.BSP/install`) | Simplification, drop the plugin entirely |
| 8 | sbt-native-packager 1.11.7 | Not needed — grep confirms it's unused in this repo (no `Universal`/`Docker`/`stage` references anywhere; README builds via `bootstrap/assembly` + `java -cp`) | Drop entirely, no replacement needed |
| 9 | sbt-updates 0.7.0 (`xdup`/`dependencyUpdates`) | No built-in or contrib equivalent found | Workaround needed — Scala Steward, or `mill show <module>.resolvedMvnDeps` + manual coursier/Maven Central queries |
| 10 | `libraryDependencySchemes += "dev.zio" %% "zio-json" % "always"` | `mvn"dev.zio::zio-json:X.Y.Z".forceVersion()` on the dep, and/or `resolutionParams`/`resolutionCustomizer` for finer eviction control | Direct equivalent (different mechanism: explicit forced dep vs. a global scheme rule) |
| 11 | `root.aggregate(coverageProjects...)` excluding `integrationTests` | No `aggregate` concept. Idiomatic options: (a) keep a `coverageProjects: Seq[JavaModule]` val (mirrors the existing sbt pattern) and drive `Task.sequence`/custom command over it, or (b) always pass an explicit module-name selector list instead of `__.compile`, since `__` walks every nested module including `integration-tests` with no built-in "exclude" glob | Workaround needed but idiomatic — reuses the project's existing pattern of an explicit list kept in sync by hand |
| 12 | CI: `scalafmtCheckAll`, `compile`, `test`, `coverageAggregate`, `bootstrap/assembly` | `mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources`, `mill __.compile` (or explicit list per #11), `mill __.test`, `mill scoverage.xmlReportAll`/`htmlReportAll`, `mill bootstrap.assembly` | Direct equivalents, contingent on #11's selector choice |

## Issues (ranked by severity)

1. **(High) Scoverage coverage-data-dir relocation is unverified for Mill.** The
   entire reason this project moved `coverageDataDir` outside `target/` was to
   survive `clean` without losing the statement catalog, and CLAUDE.md documents a
   subtle "missing-dir" gotcha tied to compile-task caching/skipping. Mill's `out/`
   directory is itself a content-addressed cache — analogous risk exists (a
   cached/skipped instrumented-compile task might not regenerate the catalog before
   `test` runs), and it's unclear from current docs whether `ScoverageModule`'s data
   destination can be pointed outside `out/` at all. This must be spiked before
   committing to the migration, not assumed solved.

2. **(Medium-High) Assembly-merge-strategy translation needs care, not a straight
   port.** Mill's `assemblyRules` matches are either exact-string
   (`Append`/`Exclude`) or regex-against-full-path
   (`AppendPattern`/`ExcludePattern`) with **first-matching-pattern-wins in list
   order** — different semantics from sbt's `PathList` segment matching plus
   pattern-match fallthrough. Concretely:
   - `PathList("META-INF", "versions", _, "module-info.class") => discard` needs a
     regex like `META-INF/versions/[^/]+/module-info\.class` (the sbt wildcard
     segment becomes a regex group).
   - The broad catch-all `PathList("META-INF", xs @ _*) => discard` must be ordered
     *after* the more specific `META-INF/services`, `META-INF/resources`,
     `META-INF/maven/org.webjars` rules in the `Seq[Rule]`, or it will shadow them
     (Mill's `.find` picks the first pattern that matches).
   - The `"first"` cases (`org/jline`, `scala/tools`, `io/getquill`,
     `compiler.properties`) need **no explicit rule at all** in Mill — its
     undocumented default behavior for anything not covered by a rule is already
     silent first-wins, which is actually good news, but also means a real,
     previously-unnoticed conflict would now be silently swallowed rather than
     erroring the way sbt-assembly does by default. Losing that "fail on unresolved
     conflict" safety net is a behavior change worth calling out explicitly if this
     migration proceeds.

3. **(Medium) No built-in "outdated dependencies" report.** `sbt xdup` has no direct
   Mill equivalent; adopting Scala Steward (external service/CI job) or scripting
   coursier queries is the realistic path. Minor process change, not a technical
   blocker, but it does remove a command from the existing `sbt xdup` workflow
   documented in CLAUDE.md's versioning policy.

4. **(Medium) `root.aggregate(...)` exclusion pattern has no direct analog.** Mill's
   `__` selector walks the whole module tree; there's no built-in "exclude this one
   nested module" glob. The safe port keeps the existing `coverageProjects`-style
   explicit list (this project already keeps one by hand for exactly this reason,
   so the idiom transfers), but CI scripts and any local "build everything" habit
   must be updated to use the explicit list/selector rather than reach for `__` and
   accidentally pull `integration-tests` (and its Testcontainers/Docker dependency)
   into a plain `mill compile` or `mill test`.

5. **(Low) `mill init` (auto-migration) won't do the real work.** It explicitly does
   not convert assembly merge strategies, scoverage config, custom multi-module
   aggregation, or non-default `testFrameworks` — so it would only scaffold the
   trivial module/dependency skeleton; the assembly, scoverage, and
   version-scheme pieces above would all be written by hand regardless.

6. **(Low) Mill version currency vs. Scala 3.3.8.** Current stable Mill is in the
   1.1.x line; Scala 3.3.8 itself is fetched via coursier independent of Mill's own
   version, so this is not expected to be a real compatibility problem — flagged
   only because it wasn't feasible to fully confirm from docs alone; worth a smoke
   test in any spike.

7. **(Informational, not a gap) sbt-native-packager and sbt-bloop can simply be
   dropped.** Grep of the repo confirms native-packager is inert (imported but
   `disablePlugins(AssemblyPlugin)` is the only per-module thing that touches
   packaging; no `Universal`/`Docker`/`stage` usage anywhere, README builds via
   `bootstrap/assembly` + `java -cp`). Bloop is superseded by Mill's built-in BSP
   server (`mill mill.bsp.BSP/install`), removing a plugin and a manual
   `bloopInstall` step from the CLAUDE.md build-commands list.

## Files relevant to a future implementation

- `build.sbt`
- `project/plugins.sbt`
- `project/Dependencies.scala`
- `project/Versions.scala`
- `.github/workflows/scala.yml`
- `CLAUDE.md` (build commands, coverage workflow, versioning policy sections)
