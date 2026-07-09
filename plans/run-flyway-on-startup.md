# Run Flyway migrations before the application starts (dev/local)

## Goal

`Main` starts the HTTP server against whatever schema the database happens to have —
`FlywayMigration.layer` exists but is unreferenced, the local dev schema was applied by hand, and
there is no `flyway_schema_history` table. Wire migrations into application startup so that in the
development/local environment the schema is always exactly what `V1`–`V7` produce, before the
server accepts its first request. If migration fails, the application must not start.

## Current state (verified against the running container, 2026-07-09)

- Live DB: the five V7-shaped tables exist (indexes/FKs match `V7` exactly), but
  `flyway_schema_history` does not — Flyway has never run here.
- A plain `migrate()` against this DB **fails mid-stream, not gently**: `V1`–`V5` are
  `CREATE ... IF NOT EXISTS` (silent no-ops), but `V4`'s
  `CREATE INDEX IF NOT EXISTS idx_routes_origin ON routes (origin_iata)` references a column `V7`
  already dropped → error at V4. Adoption of the existing DB is therefore a real problem to solve,
  not a detail.
- `FlywayMigration.migrate(url, user, password)` is a ready `Task[Unit]`; its `layer` reads the
  same `POSTGRES_*` env vars with the same defaults as `QuillDataSourceLayer`.
- `bootstrap` does not depend on the `migration` module.
- `FlywayMigrationItSpec` already proves the full `V1`→`V7` chain works from an empty database.

## Decisions

### D1 — Where migrations run: in-process effect in `Main`, before `Server.serve`

**Recommendation:** run `FlywayMigration.migrate(...)` as an explicit effect in `Main.run`,
sequenced with `*>` before the server starts:

```scala
migrateIfEnabled *> HttpServer.serve.provide(WiringModule.appLayer)
```

Explicit effect sequencing gives a deterministic order (migrate → pool → serve) that is obvious in
one line of `Main`.

Rejected alternatives:
- **Layer composition** (`FlywayMigration.layer` folded into `appLayer`): layers build in
  parallel unless dependencies force an order; making the `DataSource` depend on a `Unit`-typed
  migration layer is possible but obscures the ordering it's supposed to guarantee.
- **External step** (README instruction / script / Flyway CLI before `sbt run`): exactly the
  manual discipline that produced today's drifted, history-less database. The point is to remove
  the human step.
- **Separate migration app/job**: right answer for a real deployment pipeline someday; overkill
  for a dev-only requirement and leaves local startup unsolved.

### D2 — Environment gating: env var, default ON

**Recommendation:** `FLYWAY_MIGRATE_ON_START` (default `true`). Dev/local is the only environment
this project has, so the default serves the stated goal with zero setup; a future real environment
(or anyone who wants the old behavior) sets `false`. Add it to the env-var table in CLAUDE.md and
README.

Rejected: default `false` with opt-in — every developer would have to know about and export the
flag, recreating the manual step D1 rejects.

### D3 — Adopting the existing hand-applied dev DB: one-time reset, not baseline

**Recommendation:** one-time, per-machine procedure (documented in README, executed once):

```bash
docker compose down -v && docker compose up -d   # empty database
# start the app → Flyway applies V1..V7 from scratch
psql ... -f plans/seed-data-countries-airports.sql  # restore dev data
```

Dev data is disposable and already captured in `plans/seed-data-countries-airports.sql`; the
migration chain is proven from empty by `FlywayMigrationItSpec`; and afterwards the dev schema is
*known* to be migration-produced rather than merely believed equivalent.

Rejected alternatives:
- **`baselineOnMigrate(true)` in code:** permanently masks the "history table missing" signal on
  every database the app ever meets, and silently baselines any non-empty schema — a footgun
  encoded forever to spare one machine one reset.
- **One-time manual `flyway baseline` (insert history row at version 7):** keeps existing data,
  but certifies a hand-applied schema as migration-produced without proof; any subtle difference
  (constraint names, column defaults) becomes invisible debt. Acceptable fallback if someone's
  local data is genuinely irreplaceable — document as the exception, not the procedure.

### D4 — Config duplication: accept it

`FlywayMigration.layer` and `QuillDataSourceLayer` each read `POSTGRES_URL/USER/PASSWORD` with
identical defaults. Extracting a shared config module is out of scope; the duplication is two
files, already consistent, and both cite the same env vars documented in CLAUDE.md. Revisit only
if a third reader appears.

## Steps

1. `build.sbt`: add `migration` to `bootstrap`'s `.dependsOn(...)`. (`migration` has no domain
   dependency, so the hexagonal direction rule is untouched; the fat JAR merge strategy already
   concats `META-INF/services`, which is all Flyway needs, and the SQL files land on the JAR
   classpath where `classpath:db/migration` finds them.)
2. `Main.scala`: read `FLYWAY_MIGRATE_ON_START` (default `true`); when enabled, run
   `FlywayMigration.migrate(url, user, password)` (same env-var reads/defaults as
   `FlywayMigration.layer`) before `HttpServer.serve`. Log clearly both ways: "Flyway: applied N
   migration(s)" (already logged by `migrate`) or "Flyway: skipped (FLYWAY_MIGRATE_ON_START=false)".
   A failure fails `run` → non-zero exit, server never binds.
3. One-time local reset per D3 (each developer machine, once). Verify: `flyway_schema_history`
   exists with rows `1`–`7`, tables match, app serves requests, seed data restored.
4. Docs: CLAUDE.md — module graph (`migration` now wired into bootstrap), "Database schema"
   section (drop the "Flyway is not actually invoked anywhere yet" paragraph, describe the
   startup behavior + flag), env-var table (+`FLYWAY_MIGRATE_ON_START`); README "Running locally"
   (one-time reset procedure, flag).
5. `sbt scalafmtAll` + `sbt compile` (zero warnings), `sbt "testOnly *"`, and
   `sbt integrationTests/test` (guards the migration chain this now leans on). Manual verify:
   fresh DB → start app → history table present, endpoints work; restart → "applied 0
   migration(s)"; `FLYWAY_MIGRATE_ON_START=false` → skip logged.

## Files touched

| File | Change |
|---|---|
| `build.sbt` | `bootstrap.dependsOn(... , migration)` |
| `bootstrap/src/main/scala/.../Main.scala` | gated migrate-before-serve |
| `infrastructure/migration/.../FlywayMigration.scala` | extract `migrateFromEnv` so `Main` and `layer` share one env-reading definition |
| `plans/seed-data-countries-airports.sql` | discovered stale during the reset (still inserted via the `country_code` column V7 dropped); rewritten to resolve `country_id` via VALUES/JOIN on `countries.code` |
| `CLAUDE.md` | module graph, schema section, env-var table |
| `README.md` | one-time reset procedure, new env var |

Out of scope: wiring `OutboxRelay`, Route persistence, shared DB-config module (D4), any
production migration pipeline.
