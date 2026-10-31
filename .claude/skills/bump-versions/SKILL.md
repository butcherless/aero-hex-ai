---
name: bump-versions
description: >
  Checks for and applies dependency, SBT plugin, SBT-itself, scalafmt-formatter, and GitHub
  Actions version updates in this Scala/ZIO project, following the versioning policy in
  CLAUDE.md (LTS-only Scala, stable-GA-only deps except ZIO Prelude's RC exception, free
  patch/minor bumps, reviewed major bumps). Use this whenever the user asks to bump, upgrade, or update
  dependencies/libraries/plugins/SBT/scalafmt/GitHub-Actions, runs `sbt xdup`/`dependencyUpdates`
  and wants to act on the results, asks "are we behind on any libraries", "check for outdated
  deps", "update sbt-scoverage", "bump flyway", "check latest scalafmt version", "check GitHub
  Actions versions", "are our workflow actions up to date", or anything else about keeping this
  project's toolchain current — even when only one dependency is named.
---

# Bump versions

This project keeps versions in seven places, each needing a different check:

| What | Lives in | How to check |
|---|---|---|
| Library dependencies | `project/Versions.scala` | `sbt xdup` (alias for `dependencyUpdates`) |
| Scala language version | `project/Versions.scala` (`scala3`) | `gh api` GitHub releases, filtered to latest **LTS** |
| SBT plugins | `project/plugins.sbt` | `scripts/check_plugin_updates.py` (bundled here) |
| SBT itself | `project/build.properties` | `gh api` GitHub releases, filtered to the **1.x** line |
| scalafmt formatter | `.scalafmt.conf` (`version =`) | `gh api` GitHub `releases/latest` |
| GitHub Actions | `.github/workflows/*.yml`, `.github/actions/*/action.yml` | `scripts/check_actions_updates.py` (bundled here) |
| Container image tags | `docker-compose.yml`, `infrastructure/integration-tests/.../PostgresContainerSupport.scala` | `scripts/check_container_images.py` (bundled here) |

`xdup` only sees the main build's dependencies, not the Scala language version, `plugins.sbt`
(the meta-build), `build.properties`, `.scalafmt.conf`, the workflow/action files, or the
container image tags — all seven checks are needed for a full picture. Note that `.scalafmt.conf`'s
`version` pins the standalone scalafmt formatter, which is separate from the `sbt-scalafmt`
plugin version in `plugins.sbt` — the two are checked independently and can be a version apart
from each other. If the user named one specific dependency, skip straight to its check in Step 2
instead of running all seven — but Step 1's git sync always runs first regardless of scope,
single dependency or not.

**Fetch conventions** — every check uses one of three mechanisms, by source:
- **`sbt xdup`** for the main build's declared dependencies.
- **`gh api <path> --jq <expr>`** for anything hosted on GitHub (Scala, SBT, scalafmt, and the
  Actions script). Never `curl https://api.github.com/...` — `gh` is authenticated here (5000
  req/hr vs. 60 unauthenticated) and `--jq` removes the inline `python3 -c` parsing. `gh` being
  logged in is a prerequisite for the skill; if `gh auth status` fails, stop and say so.
- **`curl`** only for non-GitHub registries the bundled scripts hit directly — Maven Central
  (`repo1.maven.org`, SBT plugins) and Docker Hub (`hub.docker.com`, container images).

**Bundled scripts** (`check_plugin_updates.py`, `check_actions_updates.py`,
`check_container_images.py`) share conventions: they only report, never edit; they print an
aligned table with a trailing `<-- …` marker on rows needing attention (`update available`,
`MAJOR update available (review …)`, `INCONSISTENT …`, `no stable release`,
`pre-release exception`); and they exit non-zero only when the tool itself couldn't run, the one
exception being `check_container_images.py`, which also exits non-zero on a cross-file tag
mismatch (a broken state, not a suggestion).

`.github/dependabot.yml` already runs weekly `github-actions` and `docker-compose` ecosystem
updates on its own schedule and opens PRs independently of this skill — the Actions and
container-image checks here exist for the same reason the others do: an on-demand, single-pass
toolchain review, not a replacement for Dependabot. Expect them to sometimes report "up to date"
because Dependabot already caught it. The one thing Dependabot cannot see is the Testcontainers
image tag in `PostgresContainerSupport.scala` — it's a plain string literal, not a manifest
entry — so that half of the container-image check is a genuine gap-filler, not just a mirror.

## Step 1 — Sync with the remote

Pull first so the branch reflects the latest merged state — the scheduled
`.github/workflows/dependency-updates.yml` or a teammate may have already merged a bump, and
re-proposing one that already landed wastes a review cycle.

```bash
git status   # must be clean
git pull
```

If `git status` shows local changes, **abort the skill** — don't pull or stash on the user's
behalf. Tell them to commit or stash first; a version bump assumes a clean starting diff.

If the pull brings in changes to any of the version-tracking files above, note that in Step 6's
report instead of re-reporting it as this run's work.

## Step 2 — Check what's outdated

**Library deps:**
```bash
sbt xdup
```
Ignore any `org.scala-lang:scala3-library` line — that's the SBT meta-build's own Scala version,
not this project's. The output will also include updates for excluded deps (e.g. ZIO Prelude's
`zio-prelude 1.0.0-RC48 -> 1.0.0-RC50`) — leave those alone; filtering is Step 3's job, not this
one's.

If this fails with `Not a valid command: xdup`, the sbt server is stuck in the meta-build context
from an earlier `reload plugins` command. Run `sbt shutdown` and retry.

**Scala language version** (the `Versions.scala` `scala3` value — `xdup` can't tell an LTS
release from a mainline one, so check GitHub directly):
```bash
gh api 'repos/scala/scala3/releases?per_page=100' \
  --jq '[.[] | select(.prerelease | not) | select((.name // "") | test("LTS")) | .tag_name | select(test("^[0-9]+\\.[0-9]+\\.[0-9]+$"))] | max_by(split(".") | map(tonumber))'
```
Compare against `Versions.scala`'s `scala3`, numerically and using Step 3's rule. The query takes
the *highest* LTS version, never the first one listed: GitHub orders releases by date, and a patch
on an older LTS line (`3.3.9`, published after `3.9.0`) can come first. `gh` will also list newer
*mainline* releases (e.g. a `3.10.x` "Next") — the `--jq` filter drops them by design.

**SBT plugins:**
```bash
python3 .claude/skills/bump-versions/scripts/check_plugin_updates.py
```
Reports current vs. latest per plugin in `project/plugins.sbt`. Already applies the stable-only
policy and its own exception list (Step 3) — a plugin with no stable release prints
`no stable release` rather than guessing.

**SBT itself, and the scalafmt formatter** — two GitHub `/releases` checks of the same shape
(mirrors `dependency-updates.yml`'s single combined step):
```bash
# SBT — pinned to the 1.x line on purpose: CLAUDE.md forbids a 2.x jump without a migration
# review, and sbt/sbt's own /releases/latest now points at 2.x, so filter to v1.*
grep sbt.version project/build.properties
gh api 'repos/sbt/sbt/releases?per_page=30' \
  --jq '[.[] | select(.prerelease | not) | .tag_name | select(startswith("v1."))] | first'

# scalafmt formatter — single 3.x stream, so /releases/latest is safe as-is
grep '^version' .scalafmt.conf
gh api repos/scalameta/scalafmt/releases/latest --jq .tag_name
```
`.scalafmt.conf`'s `version` is the standalone formatter — separate from the `sbt-scalafmt`
plugin in `plugins.sbt` (covered by the plugin check above); the two can be a version apart.
GitHub's `/releases/latest` and the `select(.prerelease | not)` filter both exclude pre-releases.

**GitHub Actions:**
```bash
python3 .claude/skills/bump-versions/scripts/check_actions_updates.py
```
Scans every `uses: owner/repo@vN` reference across `.github/workflows/*.yml` and
`.github/actions/*/action.yml`, and reports the current floating major tag vs. the latest
release per action. Actions here are pinned to a floating major tag (`@v7`, `@v1`), not a
commit SHA — the action owner re-points `v7` as they ship `v7.x`, so minor/patch moves are
absorbed silently and `.github/dependabot.yml`'s weekly `github-actions` update only opens a PR
on a *new major*. This check therefore only flags a new major worth a release-notes read. An
`<-- INCONSISTENT ref across files` marker means the same action is referenced by different tags
in different files (shouldn't normally happen) — resolve that manually before treating it as a
clean bump.

**Container image tags:**
```bash
python3 .claude/skills/bump-versions/scripts/check_container_images.py
```
Reports current vs. latest Docker Hub tag for every image pinned in `docker-compose.yml`
(`postgres`, `confluentinc/cp-kafka`) and in the integration-tests module's
`PostgresContainerSupport.scala` (`postgres`, via `DockerImageName.parse(...)`). It understands
the two tag shapes this repo uses — `<major>[-variant]` like `18-alpine`, and full `x.y.z`
semver like `8.3.1` — and reports the newest tag of the *same* shape, so a `postgres` line
only flags a new major and a `cp-kafka` line flags any newer release. An
`<-- INCONSISTENT tag across files` marker (and a non-zero exit) means `docker-compose.yml` and
`PostgresContainerSupport.scala` pin `postgres` to different tags — the Testcontainers Postgres
must match the compose one; fix that before treating either as a clean bump. `postgres:N-alpine`
is itself a floating tag (rebuilt for every `18.x` patch), so like the GitHub Actions case only
a new *major* is ever surfaced here; `cp-kafka` is fully pinned, so its patch/minor moves show
too.

**Unversioned container images** — report only, and pass every project in a multi-project run:
```bash
python3 .claude/skills/bump-versions/scripts/check_unversioned_images.py . ../aero-ui-ai ../dependency-analyzer ../tapir-learn ../scala-3 ../incubator ../scala
```
Flags any `image:` on `:latest` or with no tag. Renovate and Dependabot never bump those, and Docker
reuses a cached `:latest`, so such a file can work locally and fail on a fresh machine. Pin the
image to a version tag; don't treat it as a routine bump, because the new version may need config
changes (`cp-kafka` 8.x, for example, refuses the old ZooKeeper settings).

## Step 3 — Apply the project's versioning policy

Read CLAUDE.md's "Versioning policy" for the full rationale. Each check has a **stability rule**,
a **version ceiling** (a major/line you never cross without a separate review), and an
**exception mechanism** (where a pre-release is tolerated) — stated uniformly below:

- **Scala** → always the latest LTS, or a newer LTS. Ceiling: never a mainline "Next" minor (e.g.
  `3.10.x`), even though `gh` lists it; the `org.scala-lang:scala3-library` line `xdup` prints is
  the meta-build's, not this project's — ignore it. Exception mechanism: a project may stay off
  LTS for tests or PoCs if it says so, in its CLAUDE.md "Versioning policy" or in a comment on the
  `Versions.scala` Scala value (none in this project today; `../scala` has one). Check for an
  exception first, then:
  - **Exception recorded** → leave the Scala version alone apart from patch bumps on its own line.
    Report it as an exception, not a deviation.
  - **Older than the latest LTS** → apply the latest LTS. No scope confirmation needed in Step 4;
    Step 5's verify gate still applies.
  - **Equal to the latest LTS, or a newer LTS** → nothing to do.
  - **On a non-LTS release with no recorded exception** → report it as a policy deviation and ask.
    Don't downgrade on your own.
- **Direct deps** → stable GA. No hard ceiling. Exception mechanism: CLAUDE.md's named-exception
  list — currently just **ZIO Prelude 1.x** (no GA release yet); stay on its pinned RC, don't
  chase a newer RC/M/SNAPSHOT without a deliberate reason (a GA release, or a needed capability).
- **SBT plugins** → stable GA. No hard ceiling. Exception mechanism: the script's
  `STABLE_ONLY_EXCEPTIONS` set (empty today), not duplicated here — a plugin listed there may
  report on a pre-release when it has no stable build.
- **SBT itself** → stable only. Ceiling: the **1.x** line — `sbt/sbt`'s `/releases/latest` now
  points at 2.x, and CLAUDE.md forbids a 2.x jump without a deliberate migration review (treat it
  as a major direct-dep bump, never a routine patch/minor). Step 2's check already filters to
  `v1.*`, so only a 1.x → 1.y bump is ever proposed. No exception mechanism.
- **scalafmt formatter** → stable only. No ceiling (single 3.x stream). No exception mechanism. A
  reformat can change files even with no rule change in `.scalafmt.conf` (new default heuristics
  between releases) — treat any resulting diff as expected, not a sign something's wrong.
- **GitHub Actions** → stable only (Actions releases don't use the RC/M/SNAPSHOT convention). No
  exception mechanism. Ceiling: pinned to a floating **major** tag — `owner/repo@v7`, never
  `@v7.0.1` and never a commit SHA — matching the workflow files; the floating tag absorbs `v7.x`
  on its own and Dependabot's weekly `github-actions` update opens a PR only on a new major, so
  this skill only ever proposes a **major** bump, and only after its release-notes read (see
  `plans/refactor-github-actions-workflows.md` G1 for why the earlier SHA-pinning convention was
  dropped).
- **Container images** → stable only. No exception mechanism. Ceiling: keep the existing tag
  *shape* — `postgres` on `<major>-alpine` (a floating tag, so only a new major is a real bump —
  read that Postgres major's release notes, same diligence as a major library bump), `cp-kafka`
  on full `x.y.z`. The Testcontainers Postgres tag in `PostgresContainerSupport.scala` and the
  `postgres` service tag in `docker-compose.yml` must always move **together, in the same
  commit** — never bump one without the other.
- **Patch/minor bumps** are safe to apply directly. **Major bumps** need a migration-guide read —
  flag separately, don't bundle them with the safe ones. For a GitHub Action, "migration guide"
  means that action's own release notes/changelog for the major version, same diligence as a
  major library bump.
- **Transitive deps** — leave to SBT's resolver; override only for a known vulnerability or
  binary-incompatibility.

## Step 4 — Confirm scope with the user

If the user named a specific dependency, do just that one. Otherwise present Step 2's findings
split into "safe to apply" (patch/minor, GA) and "needs review" (major bumps, or touching an
excluded dependency), and ask which to apply.

## Step 5 — Apply and verify each bump, one at a time

Don't batch edits. Apply exactly one version bump, then run the full verify gate, before touching
the next approved one — that way, if something breaks, it's obvious which bump did it instead of
being buried in a combined diff.

For each approved **Scala/library/plugin/SBT/scalafmt** bump, in turn:

1. Edit only that dependency's version, in whichever file it lives in — `project/Versions.scala`,
   `project/plugins.sbt`, `project/build.properties`, or `.scalafmt.conf`. A **Scala** bump also
   updates the `// x.y.z LTS` trailing comment on `ThisBuild / scalaVersion` in `build.sbt`.
2. Verify — prefix with `sbt clean` for a Scala, dependency, plugin, or SBT bump (a warm
   incremental build can mask a binary incompatibility); a bare scalafmt-formatter bump doesn't
   need it:
   ```bash
   sbt scalafmtAll
   sbt compile          # zero errors, zero warnings
   sbt test             # full suite green — CLAUDE.md's canonical command
   ```
3. If it passes, move on to the next approved bump. If it fails, stop — don't layer further bumps
   on top of a broken one. Investigate before deciding whether to revert this one, fix the fallout,
   or tell the user it needs more review than expected.

For each approved **GitHub Actions** bump, in turn — a different shape, since the ref is a bare
tag string that can appear in more than one file:

1. Take the new major tag from Step 2's script output (e.g. `v7` → `v8`).
2. Replace **every** occurrence the script listed under "used in:" — `grep -rn "owner/repo@"
   .github/` first to confirm you've found them all — so no file is left on the old major.
3. Verify: `sbt compile`/`test` don't touch `.github/`, so they verify nothing about this bump.
   Instead: confirm the YAML still parses (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" <file>`
   per changed file, or any available YAML linter) and that no reference to the old tag remains
   (`grep -rn "owner/repo@" .github/` again, expect only the new tag). The only real confirmation
   that the new action version behaves correctly is CI actually running it — flag this to the
   user as the one bump category that needs a post-push CI check, an intentional exception to
   CLAUDE.md's "don't monitor CI unless asked" default.

For each approved **container image** bump, in turn — also a bare tag string, and for `postgres`
it lives in two files:

1. Take the new tag from Step 2's script output. For `postgres`, edit **both** the `image:` line
   in `docker-compose.yml` and the `DockerImageName.parse("...")` literal in
   `PostgresContainerSupport.scala` in the same commit — re-run the script and confirm the
   `<-- INCONSISTENT` marker is gone. For `cp-kafka`, the one `docker-compose.yml` line.
2. Verify: `sbt compile`/`test` say nothing about this either. Pull the new image
   (`docker compose pull`) so the tag is proven to exist, and — for `postgres` — run
   `sbt integrationTests/test` (real Postgres via Testcontainers, needs Docker; see CLAUDE.md's
   "Integration tests" section) to confirm the new server version still passes the migration +
   repository suite. `cp-kafka` isn't wired into any automated test, so note it as
   pull-verified-only.

After the last bump passes, run `sbt bloopInstall` once to regenerate `.bloop/` (gitignored; the
`sbt-bloop` plugin is in `project/plugins.sbt`) so Metals/IDE tooling picks up the new
dependency versions — skip it if the only bumps in this run were GitHub Actions or container
images (nothing SBT-related changed).

## Step 5b — Re-sync before committing

Bumps take minutes (clean builds, full test suites), and Dependabot/Renovate or a teammate may
have merged to the remote meanwhile. Before committing, re-check for collisions:

```bash
git fetch
git status -sb   # "behind N" means the remote moved
```

If the remote is ahead, `git stash` the bump edits, `git pull`, then `git stash pop`. If the pull
touched a version-tracking file, resolve any conflict by re-reading the new value, drop bumps that
already landed, and re-run Step 5's verify gate on the final combined result before committing.

## Step 6 — Report

Cover both of these, not just the first:

- **What changed** — a short table: dependency, old version → new version, and whether its
  individual verify pass (Step 5) was clean.
- **What came up during the run** — anything unexpected, even if the run otherwise succeeded: a
  stuck sbt server needing `shutdown`, a plugin reporting `no stable release`, the skill aborting
  on local changes, an excluded dependency or major bump left untouched, a bump that failed verify
  and was reverted. This is often more useful to the user than the version diff itself, so don't
  bury it or leave it out just because the bump succeeded.
