# Refactor GitHub Actions workflows to current best practices

## Goal

`.github/workflows/scala.yml` and `.github/workflows/dependency-updates.yml` work today, but
haven't been reviewed against GitHub's own security-hardening and workflow-syntax guidance since
they were written. Review both against official docs, group the gaps, and land them as discrete,
independently-revertable changes.

## Sources consulted

- GitHub Actions docs via Context7 (`/websites/github_en_actions`): security hardening for GitHub
  Actions (SHA-pinning, script injection), workflow syntax (`permissions`, `concurrency`),
  `pull_request_target` hardening, composite actions vs. reusable workflows.
- `actions/setup-java` docs via Context7 (`/actions/setup-java`): built-in `cache: sbt` behavior —
  confirmed this already covers the Ivy/sbt/Coursier cache paths correctly; no change needed there.
- `gh api repos/{actions/checkout,actions/setup-java,sbt/setup-sbt}/releases/latest` — confirmed
  all three actions are already pinned to their current major version tag (`v7`, `v5`, `v1`); the
  gap is the *pinning format*, not staleness.

## Findings, grouped

### G1 — Pin actions to an immutable commit SHA

Both workflows reference `actions/checkout@v7`, `actions/setup-java@v5`, and `sbt/setup-sbt@v1` —
floating major-version tags that can be repointed by the action owner (or an attacker who
compromises the owner's account) without any change visible in this repo. GitHub's hardening guide
recommends pinning to a commit SHA, especially for third-party actions — `sbt/setup-sbt` is not
GitHub-owned, so it's the highest-value target here; `actions/checkout`/`setup-java` are
GitHub-owned and lower-risk but the same guidance applies.

Fix — pin all three, in both files, keeping the version as a trailing comment (GitHub's own
convention so bumps stay reviewable in a diff):

```yaml
uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
uses: actions/setup-java@0f481fcb613427c0f801b606911222b5b6f3083a # v5.5.0
uses: sbt/setup-sbt@12b8b25f7dab04860144e2dd48dc141b485192f7 # v1.5.0
```

6 call sites total (3 actions × 2 files).

### G2 — Harden checkout: don't persist git credentials

`actions/checkout` defaults to `persist-credentials: true`, leaving the job's `GITHUB_TOKEN` in
local git config for every step that runs afterward. Neither workflow pushes or needs
git-authenticated operations post-checkout, so the token sitting in `.git/config` is pure unused
attack surface (e.g. a compromised sbt plugin pulled transitively could read and misuse it).

Fix: add `with: persist-credentials: false` to both `Checkout` steps.

### G3 — Remove the dead "Integration tests" step

`scala.yml` has:

```yaml
- name: Integration tests
  if: false
  run: echo "not yet implemented"
```

This is stale and now actively inaccurate — CLAUDE.md documents that `integration-tests` exists,
passes 36 tests, and is *deliberately* excluded from `root`'s aggregate, invoked explicitly via
`sbt integrationTests/test`. A permanently-`if: false` step with a "not yet implemented" message
misdescribes that as a TODO rather than a design decision.

Fix: delete the step. Wiring integration tests into CI (behind the commented-out Postgres/Kafka
`services:` block already sketched in the file) is a real, separate decision — start a new plan
doc for it rather than resurrecting this placeholder.

### G4 — De-duplicate the shared setup steps

`Checkout` → `Setup JDK` → `Setup SBT` — same three steps, identical `with:` blocks — are
copy-pasted in both workflow files. GitHub's own guidance for step-level duplication within a repo
is a composite action. Low severity today (3 steps × 2 files), but it compounds: every SHA bump
from G1 would otherwise need to land in two places.

Fix: extract `.github/actions/setup-build-env/action.yml` (composite: checkout + setup-java +
setup-sbt), consumed from both workflows as a single `uses: ./.github/actions/setup-build-env`
step.

Trade-off, accepted: composite actions collapse their inner steps into one entry in the Actions UI
log — acceptable here since these are boilerplate infra steps, not steps whose individual
timing/output is normally inspected.

**Correction (landed, then broke CI, then fixed — run 29045506506):** checkout can't be the first
step *inside* a local composite action. `uses: ./.github/actions/setup-build-env` is resolved
against the already-checked-out workspace, so by the time the runner can even read that action's
`action.yml`, checkout must already have happened — putting `actions/checkout` inside the action
is circular. Failed with `Can't find 'action.yml' ... Did you forget to run actions/checkout before
running your local action?`, 4 seconds into the very first push after landing G1–G5. Fix: `Checkout`
stays an explicit first step in each workflow (still SHA-pinned per G1, still
`persist-credentials: false` per G2); the composite action only bundles Setup JDK + Setup SBT.
De-duplication scope for G4 is smaller than originally planned (2 of 3 steps, not 3 of 3), but
still real.

### G5 — Trigger and observability gaps

- `scala.yml` has no `workflow_dispatch`, unlike `dependency-updates.yml` — add it so a run can be
  manually re-triggered from the Actions UI (exactly what we just did by hand via
  `gh run rerun --failed` to confirm the sbt-thin-client flake).
- `dependency-updates.yml` has no `concurrency` block; `scala.yml` does. Add the same
  `group: ${{ github.workflow }}-${{ github.ref }}` / `cancel-in-progress: true` for consistency —
  low-risk given it's a weekly cron, but a manual dispatch overlapping the Monday cron would
  otherwise run twice for no reason.
- `sbt dependencyUpdates` output only goes to the raw step log. Pipe it to `$GITHUB_STEP_SUMMARY`
  so outdated-dependency results show up on the run's summary page without opening logs.

## Explicitly out of scope / rejected

- **Re-enabling the commented-out Postgres/Kafka `services:` block** — belongs to a future "wire
  integration tests into CI" plan, not this hardening/cleanup pass (see G3).
- **Bumping actions to newer majors** — all three are already the current major release; only the
  pinning *format* changes (G1).
- **`ubuntu-24.04` → `ubuntu-latest`** — pinning the OS version is itself the recommended practice
  for reproducible builds; not touching it.

## Implementation order

G3 (delete dead step) → G1 + G2 (same lines, land together) → G4 (composite action) → G5 (small,
independent additions). G3 goes first because it changes the line numbers G1 touches.

## Steps

1. `scala.yml`: delete the `Integration tests` step (G3).
2. Both files: pin `actions/checkout`, `actions/setup-java`, `sbt/setup-sbt` to the SHAs above with
   version comments; add `persist-credentials: false` to both `Checkout` steps (G1 + G2).
3. Add `.github/actions/setup-build-env/action.yml` (composite: checkout + setup-java + setup-sbt,
   with `persist-credentials: false` baked in); replace the three individual steps in both
   workflows with one `uses: ./.github/actions/setup-build-env` step (G4).
4. `scala.yml`: add `on.workflow_dispatch: {}`. `dependency-updates.yml`: add the same
   `concurrency` block as `scala.yml`; pipe `sbt dependencyUpdates` output to
   `$GITHUB_STEP_SUMMARY` (G5).
5. Push a trivial commit (or open a PR) to confirm both workflows still run green end-to-end —
   this is CI config, so the only real verification is watching an actual run.

## Files touched

| File | Change |
|---|---|
| `.github/workflows/scala.yml` | G1–G5 |
| `.github/workflows/dependency-updates.yml` | G1, G2, G4, G5 |
| `.github/actions/setup-build-env/action.yml` | new (G4) |

Out of scope: wiring integration tests into CI, any change to the `runs-on` OS pin, any action
major-version bump.
