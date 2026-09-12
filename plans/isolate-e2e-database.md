# Isolate the CRUD-lifecycle E2E folders from the shared dev database

## Goal

Make the `run-e2e-tests` skill's 5 `E2E — ...` Postman folders (Country/Airport/Airline/Aircraft
CRUD lifecycles + Country search) pass repeatably, regardless of what real reference data has
been loaded into the developer's long-lived dev Postgres via the `master-data-sync` module.

## Problem

`run-e2e-tests` deliberately reuses the standard local dev database
(`aero-hex-ai-postgres-1` from `docker-compose.yml`) and leaves it running afterward — see that
skill's own doc. This is fine for read-only/search-style E2E scenarios, but the CRUD-lifecycle
folders hardcode natural keys they expect to be free to create:

- `CountryCode` only accepts a **recognized ISO 3166-1 alpha-2 code**
  (`domain/country/IsoCountryCodes.scala`'s `validateIso`) — there is no "obviously fake" code a
  test can safely invent, unlike `IataCode`/`AirlineIcaoCode`/`Registration`, which only check
  shape, not membership in a real registry.
- Once `master-data-sync` has been run for Country (see the module graph in the root `CLAUDE.md`),
  **all 249 valid ISO codes already exist** in the dev DB. Confirmed directly: `select count(*)
  from countries` returns 249 on a synced dev DB.
- The Country CRUD lifecycle folder's "Create country" step hardcodes `PT` (Portugal). Once
  synced, that code — and every other valid code — is permanently taken, so `POST
  /api/v1/countries` returns `409 Conflict` instead of `201`, `pm.environment.set('countryCode',
  ...)` never fires from the response body, and every subsequent chained step in that folder (and
  the Country-dependency setup step in Airport/Airline/Aircraft's folders) fails downstream from
  that single `undefined` variable.

This is not a "pick a different fixture code" problem — it's structural. There is no ISO code
left to pick. The fixture strategy (hardcode a real code, assume it's free) is fundamentally
incompatible with a dev DB that has ever run `master-data-sync`'s Country sync.

## Decision

**Run the 5 CRUD-lifecycle E2E folders against a dedicated, ephemeral Postgres — migrated only,
never master-data-synced — instead of the shared dev DB.** Tear it down after the run.

This mirrors a decision this project already made once, for the same reason: the opt-in
`integration-tests` module runs each spec against a **fresh Testcontainers Postgres**
specifically so real state from one test can't leak into another (see
`plans/add-persistence-integration-tests.md`'s "why fresh-container-per-suite"). E2E has the
identical requirement one layer up — a fresh, schema-only database per run — just against the
full HTTP server instead of a repository directly.

### Options considered

| Option | Verdict |
|---|---|
| **Ephemeral Postgres, migrated only, torn down after the run** | **Recommended.** Matches `integration-tests`' already-accepted design; guarantees every ISO code is free every run; no risk to real dev data. |
| Delete-then-recreate the target country/airport/airline/aircraft against the shared dev DB | Rejected. Deleting `PT`/`KI`/etc. destroys real `master-data-sync` reference data other tools/developers rely on (24 real Portuguese airports, in this case) — a test run should never have a destructive side effect on shared state. Also racy if two people (or CI + a developer) run E2E concurrently against the same dev DB. |
| Document "run E2E before ever running master-data-sync" | Rejected. Silent, implicit, and exactly the trap this investigation fell into — it breaks the moment anyone follows the (equally documented, equally legitimate) master-data-sync recipe, with no error pointing back at the ordering assumption. |
| Give `CountryCode` an escape hatch for a reserved/fake test code | Rejected. `CountryCode` intentionally only accepts real ISO codes (`docs/analysis/validation-analysis-hexagonal.md`) — weakening that to satisfy a test fixture would be a real (if small) domain-integrity regression, and ISO 3166-1's own reserved/user-assigned range (e.g. `ZZ`) isn't guaranteed stable or exempt from `validateIso`'s allowlist either. |
| Keep the shared dev DB, but only for Airport/Airline/Aircraft (fake-key entities), move only Country CRUD to ephemeral | Rejected as inconsistent. Airport/Airline/Aircraft folders all resolve a Country dependency first, which hits the exact same collision through their own "Create country dependency" step — the whole lifecycle group needs the same fix. |

## Design

- Reuse the existing `docker-compose.yml` `postgres` service **definition** but start a second,
  disposable container from it under a different name/port (e.g. `docker compose run --rm -p
  5433:5432 postgres`, or a plain `docker run postgres:18-alpine` with the same env vars) —
  no need for a new service block.
- Run `FlywayMigration` against it (same mechanism `Main` already uses at startup) to get an
  empty, correctly-shaped schema — no master data, no prior test residue.
- Point the app instance used for this run at that ephemeral database (`DB_HOST`/`DB_PORT` env
  vars, however `WiringModule`/`QuillDataSourceLayer` currently sources them — confirm exact var
  names before implementing) instead of the default `localhost:5432`.
- Run only the 5 `E2E — ...` folders against that instance, exactly as today.
- Tear the ephemeral container down unconditionally on exit (success, failure, or interruption —
  same `trap`-based cleanup `run-e2e-tests`' script already uses for the app process).
- Leave the shared dev Postgres (`aero-hex-ai-postgres-1`) completely untouched — this change is
  additive to `run-e2e-tests`, not a replacement for local dev workflows that still want to poke
  at the real seeded data manually via Swagger/curl.

## Rejected non-goal

Making the CRUD-lifecycle folders' fixture keys dynamic/random (e.g. pick a random unused
2-letter combination) doesn't fully solve it either, since `CountryCode.validateIso` still
requires *recognized* ISO membership, not just shape — "random 2 letters" has no better odds of
being free than a hardcoded one once the table is fully synced. The database has to be the thing
that's fresh, not the fixture value.

## Steps (not yet implemented)

1. Confirm the exact Postgres connection env vars `WiringModule` reads (likely
   `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` per `application.conf`'s `${?...}"`
   overrides — verify against that file).
2. Add an ephemeral-Postgres start/migrate/teardown block to
   `.claude/skills/run-e2e-tests/scripts/run.sh`, gated so it only applies to this skill (not
   `sync-postman-collection`'s own Step 6 Newman verification, which can keep using the shared
   dev DB for its non-CRUD-lifecycle regression check — or apply the same fix there too, for the
   same reason, if its E2E verification pass ever exercises these folders).
3. Update `run-e2e-tests`' own doc (this file's sibling, `.claude/skills/run-e2e-tests/SKILL.md`
   or equivalent) to describe the new ephemeral-DB step.
4. Re-run the 5 folders end-to-end against a dev machine that already has `master-data-sync`
   fully applied to its shared dev DB, to confirm the collision is gone.

## Files touched (when implemented)

- `.claude/skills/run-e2e-tests/scripts/run.sh`
- `.claude/skills/run-e2e-tests/SKILL.md` (or wherever its steps are documented)
- No application code changes — this is purely a test-workflow fix.
