# Configure Dependabot version updates

## Goal

`.github/dependabot.yml` is an unfilled template (`package-ecosystem: ""`, i.e. non-functional —
Dependabot never runs). Replace it with a real configuration, scoped to what this repo actually
has, and reconciled with the versioning policy already documented in CLAUDE.md (Scala LTS-only,
stable-GA-only deps except Doobie's RC exception, free patch/minor vs. reviewed major bumps).

Trigger: the workflow-hardening plan ([[refactor-github-actions-workflows]],
`plans/refactor-github-actions-workflows.md`, G1) pins GitHub Actions to commit SHAs — those need
something to bump them, or they silently go stale.

## Analysis — what ecosystems apply here

Checked against the official Dependabot `package-ecosystem` table (GitHub Docs, fetched directly —
Context7's indexed copy of this specific table was truncated) and this repo's actual files:

| Ecosystem | YAML value | Present in repo? | Manifest(s) found |
|---|---|---|---|
| GitHub Actions | `github-actions` | Yes | `.github/workflows/*.yml`, `.github/actions/*/action.yml` |
| sbt | `sbt` | Yes | `build.sbt`, `project/plugins.sbt`, `project/build.properties`, `project/Versions.scala`, `project/Dependencies.scala` — matches Dependabot's documented sbt file patterns exactly |
| Docker Compose | `docker-compose` | Yes | `docker-compose.yml` (root) — `postgres:16-alpine`, `confluentinc/cp-kafka:8.3.0` |
| Docker | `docker` | **No** | No `Dockerfile` anywhere in the repo (verified with `find`) — enabling this ecosystem would error with no manifest found |

`docker` and `docker-compose` are two *separate* YAML values (confirmed against the official
table, correcting an earlier assumption that `docker` covers both) — only `docker-compose` applies
here.

## Decisions

### D1 — Enable `github-actions`, `sbt`, and `docker-compose`; skip `docker`

**Recommendation:** one `updates:` entry per applicable ecosystem, all `directory: "/"` (Dependabot
auto-discovers the standard file locations for each — no need to enumerate `project/*.scala`
files individually).

Rejected: adding `docker` — there's no `Dockerfile`, so it would fail outright.

### D2 — Guard-rail the `sbt` ecosystem to match CLAUDE.md's versioning policy

**Recommendation:** the `sbt` block is the one that needs constraints — left wide open it will
directly contradict decisions already made and documented:

```yaml
ignore:
  - dependency-name: "*"
    update-types: [ "version-update:semver-major" ]   # "major bumps need migration-guide review"
  - dependency-name: "org.tpolecat:doobie-*"           # "don't chase a newer RC without a deliberate reason"
```

- The global major-version ignore matches "Patch/minor updates are free; major bumps need
  migration-guide review and passing compile + tests" — Dependabot still *opens* patch/minor PRs
  automatically, but major bumps stay manual (via the `bump-versions` skill), exactly as today.
- Doobie is excluded from *all* update types, not just majors: the policy isn't "Doobie majors need
  review," it's "don't chase Doobie RCs at all without a reason" — even a Dependabot-proposed
  RC9→RC10 patch bump would be an unwanted auto-nudge against a deliberate pin.

Rejected: no `ignore` rules (fully automatic) — the first "helpful" major-version or Doobie PR
Dependabot opens would be exactly the kind of unreviewed churn the policy exists to prevent.

**Open question, not blocking:** `scalaVersion := "3.3.8"` in `build.sbt` is a build setting, not a
`libraryDependencies` entry — unclear whether Dependabot's sbt updater treats it as a trackable
dependency at all. Don't pre-guess the `dependency-name` it would use; watch the first Dependabot
scan, and if it proposes a Scala bump, add a targeted `ignore` then (LTS-only is non-negotiable per
CLAUDE.md, so any such PR would need to be blocked either way).

### D3 — Keep the existing custom "Dependency Updates" workflow alongside Dependabot, for now

**Recommendation:** don't touch `.github/workflows/dependency-updates.yml`. It's a read-only weekly
report (`sbt dependencyUpdates` → step summary, per the pending draft); Dependabot's `sbt` ecosystem
will start opening real PRs. They overlap in *coverage* but differ in *action* — the overlap is
cheap insurance while trust in Dependabot's sbt support builds up (GitHub's own table lists sbt's
"Supported versions" as "Not applicable," consistent with it being one of the newer/thinner
ecosystem integrations).

Rejected: retire the custom workflow now — premature before seeing Dependabot actually open and
pass CI on a real PR against this repo's dependency *indirection* (versions live in a separate
`project/Versions.scala` object referenced from `project/Dependencies.scala`, not as inline string
literals in `build.sbt` — a pattern some ecosystem parsers only partially resolve). If the `sbt`
block turns out to silently find nothing or mis-parse versions, this is the fallback safety net.
Revisit retiring it only after a Dependabot sbt PR has actually landed and gone green.

### D4 — Schedule: weekly, Monday

**Recommendation:** `schedule: interval: weekly`, `day: monday` on all three blocks, matching the
existing custom workflow's Monday 08:00 UTC cadence so dependency-related activity (report +
Dependabot PRs) lands on the same day. Not worth pinning an exact `time:` to match — close enough.

### D5 — Skip `groups:` for now

**Recommendation:** don't group related dependencies (e.g. the `zio`/`zio-http`/`zio-kafka`/
`zio-logging` family) into single PRs yet. It's a real option for reducing PR noise later, but this
repo has ~15 direct deps across modules — not enough observed noise yet to justify the extra config
surface. Revisit if Dependabot PR volume becomes annoying in practice.

## Steps

1. Replace the placeholder body of `.github/dependabot.yml` with three `updates:` entries
   (`github-actions`, `sbt`, `docker-compose`), each `directory: "/"`, `schedule: weekly` / `monday`
   (D1, D4); `sbt` additionally carries the `ignore` rules from D2.
2. Validate YAML syntax.
3. Push and watch the repo's Insights → Dependency graph → Dependabot tab (or trigger a manual
   check if the UI allows it) to confirm: (a) all three ecosystems find their manifests with no
   error, (b) the `sbt` ecosystem correctly resolves the `Versions.scala`/`Dependencies.scala`
   indirection instead of silently finding zero dependencies.
4. If `sbt` fails to parse correctly, drop that block (keep `github-actions` + `docker-compose`)
   and note the limitation in this doc rather than fighting the parser.

## Files touched

| File | Change |
|---|---|
| `.github/dependabot.yml` | replace empty placeholder with real `github-actions` / `sbt` / `docker-compose` config |

Out of scope: retiring `dependency-updates.yml` (D3), grouping (D5), a targeted Scala-version
`ignore` rule (D2's open question — add only if actually needed).
