# Plan: optional, non-blocking integration tests for the database model and persistence layer

## Goal

Add integration tests that exercise the real database model and persistence layer
(migrations, `QuillCountryRepository`, `DoobieAirportRepository`, and the other
Doobie repositories) against a real Postgres — not in-memory stubs or the Tapir
stub server this project's existing tests use. These tests must be **optional**:
never run by `sbt compile`, `sbt "testOnly *"`, or `sbt coverageAggregate`, and never
block the normal build or CI gate described in `CLAUDE.md`. A developer opts in
explicitly when they want to check the database model.

This directly automates what was just done by hand to validate the
`V7__add_surrogate_keys.sql` migration (see
`plans/surrogate-long-keys-country-airport.md`): a manual `\d` inspection plus
hand-run `curl`/`psql` checks against the dev container. An integration test suite
turns that one-off verification into a repeatable regression check.

> **Revision note (2026-07-04):** this plan originally recommended sbt's built-in
> `IntegrationTest` configuration (`.configs(IntegrationTest)` + `Defaults.itSettings`,
> `src/it/scala`). That mechanism is **deprecated as of sbt 1.9.0** (RFC-3: sbt is
> deprecating general use of the configuration axis beyond `Compile`/`Test`) and the
> official sbt 2.x migration guide says explicitly: *"To migrate away from the
> `IntegrationTest` configuration, create a separate subproject and implement it as
> normal test."* (verified against `https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html`
> and the sbt 1.9.0 release notes). Decisions 1 and 3 below are rewritten accordingly.
> Everything else in the original plan (scope table, container-lifecycle idiom,
> naming convention, non-blocking guarantees) still holds and is carried over.

## Design decisions to confirm before implementing

### Decision 1 — dedicated `integration-tests` subproject, plain `Test` config (sbt 2.x correct approach)

Per sbt's own migration guidance, integration tests belong in a **separate subproject**
using the standard `Test` configuration — not a scoped configuration inside an existing
module. The "optional and non-blocking" property no longer comes from a structurally
separate configuration axis (that mechanism is gone); it comes from **subproject
aggregation**: the new module is simply never added to the root project's
`.aggregate(...)` list, so none of the root-scoped commands (`sbt compile`,
`sbt "testOnly *"`, `sbt coverageAggregate`) ever cascade into it. This is still a
structural guarantee, not a tag/filter one — a developer would have to deliberately
add the module to root's `aggregate(...)` to break it, the same class of mistake the
old `IntegrationTest` config protected against.

```scala
lazy val integrationTests = project
  .in(file("infrastructure/integration-tests"))
  .dependsOn(migration, persistencePostgres, persistenceQuill)
  .settings(
    name             := "integration-tests",
    publish / skip   := true,
    Test / fork      := true, // isolates the Docker/Testcontainers lifecycle from sbt's own JVM
    libraryDependencies ++= Seq(zioTest, zioTestSbt, testcontainersCore, testcontainersPostgres).map(_ % Test),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .disablePlugins(AssemblyPlugin)
// deliberately NOT added to `root`'s .aggregate(...) list — see Decision 1
```

Reuses the project's existing `zioTest`/`zioTestSbt` vals as-is (plain `% Test`,
same as every other module) — no `*IT`-suffixed dependency variants needed, since
there's no second configuration axis anymore. Only two genuinely new
`Dependencies.scala` vals are needed:

```scala
val testcontainersCore     = "org.testcontainers" % "testcontainers" % Versions.testcontainers
val testcontainersPostgres = "org.testcontainers" % "postgresql"     % Versions.testcontainers
```

**Rejected:** keeping `.configs(IntegrationTest)` — deprecated, and would carry a
migration cost the moment this project ever moves to sbt's next major line where the
mechanism is removed entirely.

### Decision 2 — Testcontainers Java directly, no Scala wrapper (recommended)

Use the plain `org.testcontainers:testcontainers` + `org.testcontainers:postgresql`
Java artifacts and wrap the container lifecycle in `ZIO.acquireRelease` /
`ZLayer.scoped` — the same idiom this project already uses for
`QuillDataSourceLayer.live` and `PostgresConfig.transactorLayer`:

```scala
val containerLayer: TaskLayer[PostgreSQLContainer[?]] = ZLayer.scoped {
  ZIO.acquireRelease(
    ZIO.attempt {
      val c = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
      c.start()
      c
    }
  )(c => ZIO.attempt(c.stop()).ignoreLogged)
}
```

**Rejected:** `testcontainers-scala` — a second, less-established dependency with its
own ZIO-2/Scala-3 compatibility surface to track, for a thin lifecycle wrapper this
project can write directly in ~10 lines using patterns already established in the
codebase. Also rejected: the `jdbc:tc:postgresql:...` magic JDBC URL prefix — it hides
container lifecycle from ZIO's resource management entirely, which fights the
`ZLayer.scoped` style used everywhere else in this codebase.

**Gotcha found during validation (2026-07-04):** on a machine with a recent Docker
Desktop, container startup failed with a misleading
`IllegalStateException: Could not find a valid Docker environment` even though Docker
was running and reachable. Root cause: Testcontainers 1.21.x's environment-detection
probe falls back to a hardcoded, old Docker API version (`1.32`) when none is
negotiated, and this Docker Desktop's engine (`MinAPIVersion: 1.40`) rejects it with a
400. The failure was silent by default (no SLF4J binding on the module's test
classpath to surface Testcontainers' own warn/error logs) — `logback % Test` was added
to `integrationTests`' dependencies specifically so this class of failure is
diagnosable, not just "tests failed." The actual fix is
`Test / javaOptions += "-Dapi.version=1.41"` in `build.sbt`, pinning docker-java to a
modern API version so the stale probe default is never used. Also note: **a stale,
detached sbt server process ignores shell environment changes** (Java version
switches, env vars) made in a later terminal session — if a fix that should apply
(env var, `sdk use`) seems to have no effect, check `ps aux | grep sbt-launch` and
kill/restart the server.

### Decision 3 — shared container+migration bootstrap: a plain object inside the one new module (supersedes the old testkit-module question)

The original plan wrestled with whether cross-module duplication of container+Flyway
bootstrap logic justified a new `persistence-testkit` module. That question is now
moot: since sbt 2.x collapses everything into **one** `integration-tests` subproject
(Decision 1) rather than three separate `src/it/scala` trees, there is no cross-module
duplication to solve. The shared bootstrap is just a plain object living in the same
subproject as the specs that use it:

`infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/support/PostgresContainerSupport.scala`
— exposes `containerLayer: TaskLayer[PostgreSQLContainer[?]]` (Decision 2, raw and
unmigrated — used by `FlywayMigrationItSpec` itself, which is the one spec that needs
to run the migration as its own test action rather than as fixture setup) and
`migratedContainerLayer: TaskLayer[PostgreSQLContainer[?]]`, which calls
`FlywayMigration.migrate(url, user, password)` (the real `migration` module's function,
not its `.layer` — that reads `POSTGRES_URL`/etc. from `sys.env`, which doesn't fit a
container with a randomly assigned port) against the started container. `dataSourceLayer`
and `transactorLayer` both compose on top of `migratedContainerLayer` to hand back a
`HikariDataSource` / `Transactor[Task]` respectively, for the Quill and Doobie specs.

Spec packages organize by what they cover, mirroring the original scope table:
`dev.cmartin.aerohex.it.migration`, `.it.postgres`, `.it.quill`.

### Decision 4 — naming convention: `*ItSpec`

Existing unit specs use `*Spec` (`AirportEndpointsSpec`, `CountryEndpointsSpec`).
`*ItSpec` mirrors that convention while immediately signaling "this needs Docker and a
real DB." Preferred over a bare `*IT` suffix (breaks from the project's established
`ZIOSpecDefault`/`*Spec` naming, and reads ambiguously next to `*Spec` in a directory
listing).

## Scope — which specs get written

| Package (in `integration-tests`) | Spec | Covers | Status |
|---|---|---|---|
| `it.migration` | `FlywayMigrationItSpec` | Fresh container, run all `V1`–`V7` migrations via `FlywayMigration.migrate`, assert success and that `flyway_schema_history` reaches `V7`. This is the automated version of the manual `\d countries`/`\d airports` check just done by hand — it would have caught the original FK-dependency ordering bug in `V7` (see `plans/surrogate-long-keys-country-airport.md`, Issue 1) without needing manual review. | ✅ implemented, green (2026-07-04) |
| `it.postgres` | `DoobieCountryRepositoryItSpec` | The unwired-but-schema-consistent repository: `save`/`findAll`/`searchByName`/`update`/`delete` round trip against a migrated container, including both `*NotFound` failure paths | ✅ implemented, green (2026-07-04) |
| `it.quill` | `QuillCountryRepositoryItSpec` | The wired, real repository: `findByCode`/`findAll`/`searchByName`/`save`/`update`/`delete` round trip, including `CountryAlreadyExists` and both `*NotFound` failure paths | ✅ implemented, green (2026-07-04) |
| `it.postgres` | `DoobieAirportRepositoryItSpec` | Unwired-but-schema-consistent repository: `save`/`findByIata`/`findAll`/`searchByName`/`findByCountry`/`update`/`delete` round trip against a migrated + seeded container; explicit cases for `save`/`update` with an unknown `countryCode` asserting `DomainError.CountryNotFound` (the `resolveCountryId` path added in the surrogate-key work), plus `AirportNotFound`/`AirportAlreadyExists` | ✅ implemented, green (2026-07-04) |
| `it.quill` | `QuillAirportRepositoryItSpec` | The wired, real repository — same coverage as the Doobie spec above (Quill's `AirportRepository` has the identical `resolveCountryId` pattern and the same not-found/already-exists error cases) | ✅ implemented, green (2026-07-04) |
| `it.postgres` | `DoobieAirlineRepositoryItSpec`, `DoobieRouteRepositoryItSpec` | Unwired today, but real SQL that should stay correct — would have caught the pre-existing `DoobieAirlineRepository.foundation_date` column mismatch noted during the surrogate-key review immediately, instead of silently at some future wiring point | ⬜ not yet implemented |

Country was implemented first, as a validation slice for the whole approach (module
structure, aggregation exclusion, Testcontainers lifecycle, Flyway bootstrap) — see the
Decision 2 gotcha note above for what that validation surfaced. Airport followed the same
`PostgresContainerSupport` layers with no further structural decisions needed; each
Airport test seeds its own `Country` row first (via the corresponding `CountryRepository`
implementation, same `DataSource`/`Transactor`) since `airports.country_id` FKs to
`countries.id`. Note neither Airport repository's `delete` checks the affected row count
(unlike Country's), so deleting an unknown `iata` silently succeeds rather than failing
with `AirportNotFound` — this is existing repository behavior, not a test gap. Airline
and Route remain unimplemented.

Each spec migrates a **fresh** container per suite (not shared/reused across suites) —
simplest correctness story, avoids state bleeding between specs, and Testcontainers'
container startup (~1–2s for `postgres:16-alpine`) is cheap enough that this isn't a
meaningful runtime cost for an opt-in suite across six specs.

**Bug found during validation (2026-07-04):** the first working version used
`.provideLayer(PostgresContainerSupport.xxxLayer)` on each `suite(...)`. `provideLayer`
does **not** share a layer across a suite's tests — ZIO Test rebuilds it once per `test`
block. A run of the three Country specs (15 tests total) started **16 separate Postgres
containers** (confirmed via `grep "started in PT"` in the run log), one per test case
instead of one per suite, each paying the full container-start + Flyway-migrate cost.
Fix: use **`provideLayerShared`** instead — it builds the layer once and shares it
across every test in that `suite(...)` block, matching the "fresh container per suite"
design actually intended here. After the fix, the same three specs started exactly 3
containers (confirmed the same way) and suite wall-clock dropped from ~9s to ~2.8s.
**Every new `*ItSpec` must use `provideLayerShared`, never `provideLayer`,** or it
silently reverts to one container per test. The tests were written with `contains`/
`exists`-style assertions rather than exact-list equality specifically so that sharing
one container/database across a suite's tests is safe regardless of execution order.

(Rejected: a single
container shared across all specs via a ZIO Test shared layer — the analysis that
originally suggested this also required `Test / parallelExecution := false` to avoid
races on shared tables; fresh-per-suite containers sidestep that entirely and keep
specs independent, at the cost of a few extra seconds of total runtime that doesn't
matter for a manually-invoked suite.)

## Non-blocking guarantees — explicit checklist

- `sbt compile` — unaffected; `integrationTests` is not in root's `.aggregate(...)`.
- `sbt "testOnly *"` — unaffected, same reason: not aggregated, so root-scoped test
  commands never reach it.
- `sbt coverageAggregate` — unaffected; **no `coverageSettings` applied** to
  `integrationTests` (it's test-support/verification code, not production logic to
  measure) and it isn't aggregated in the first place.
- CI workflow (per `CLAUDE.md`'s coverage section: `<module>/testOnly *` per module) —
  unaffected; CI invokes modules by explicit name and `integrationTests` is never named.
- Requires Docker running locally to invoke at all (Testcontainers). Not adding
  Docker-availability detection/auto-skip in this plan — out of scope; a developer who
  runs `integrationTests/test` without Docker gets a clear container-startup failure,
  which is an acceptable opt-in cost.
- New optional command alias for convenience, alongside the existing `xdup` alias:
  `addCommandAlias("integrationTest", "integrationTests/test")`.
- **Explicitly out of scope:** wiring these into the CI workflow. The ask here is
  local/manual opt-in via `sbt`; whether CI should ever run a Docker-dependent job is a
  separate decision with its own tradeoffs (CI runner Docker-in-Docker support, runtime
  cost per PR) not addressed by this plan.

## Verification

- `sbt scalafmtAll && sbt compile` — zero warnings; confirms `integrationTests` doesn't
  leak into the default `compile` task (it isn't aggregated).
- `sbt "testOnly *"` still passes and still excludes the new specs.
- `sbt integrationTests/test` runs green against real, freshly-started containers
  (six specs, one container each).
- `sbt dependencyUpdates` — confirm the Testcontainers version pinned here is still the
  current stable GA at implementation time (per `CLAUDE.md`'s versioning policy); this
  plan intentionally doesn't hardcode a version number for that reason.

## Files touched (summary)

- `build.sbt` — new `integrationTests` project (Decision 1); deliberately **not**
  added to `root`'s `.aggregate(...)`; new `integrationTest` command alias
- `project/Dependencies.scala` — `Versions.testcontainers`, `testcontainersCore`,
  `testcontainersPostgres`
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/support/PostgresContainerSupport.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/migration/FlywayMigrationItSpec.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/postgres/DoobieAirportRepositoryItSpec.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/postgres/DoobieCountryRepositoryItSpec.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/postgres/DoobieAirlineRepositoryItSpec.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/postgres/DoobieRouteRepositoryItSpec.scala` (new)
- `infrastructure/integration-tests/src/test/scala/dev/cmartin/aerohex/it/quill/QuillCountryRepositoryItSpec.scala` (new)
- `CLAUDE.md` — document the `sbt integrationTests/test` / `sbt integrationTest`
  workflow as opt-in, Docker-dependent, and separate from the `testOnly *`/coverage
  workflow already documented; note the module is intentionally excluded from root
  aggregation
