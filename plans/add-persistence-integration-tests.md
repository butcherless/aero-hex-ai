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

## Design decisions to confirm before implementing

### Decision 1 — sbt's built-in `IntegrationTest` configuration (recommended)

sbt ships a standard `IntegrationTest` configuration for exactly this purpose:
sources live in `src/it/scala`, tests run via `sbt IntegrationTest/test` (or
`sbt it:test`), and — critically — this configuration is **structurally separate**
from `Test`. `sbt compile`, `sbt "testOnly *"`, and `sbt coverageAggregate` never
touch it. This gives "optional and non-blocking" for free, with no custom task
wiring, no `Tags`/`exclude` gymnastics, and no risk of accidentally being picked up
by the existing CI workflow (per `CLAUDE.md`'s coverage section, CI runs
`<module>/testOnly *`, which is a different configuration than `IntegrationTest`).

Per-module wiring needed (`build.sbt`):

```scala
lazy val persistencePostgres = project
  .in(file("infrastructure/persistence-postgres"))
  .dependsOn(domain)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    name := "persistence-postgres",
    libraryDependencies ++= Seq(doobieCore, doobieHikari, doobiePostgres, zioInteropCats, postgresql, hikaricp),
    libraryDependencies ++= Seq(zioTestIT, zioTestSbtIT, testcontainersCore, testcontainersPostgres),
    IntegrationTest / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
  .settings(coverageSettings*)
  .disablePlugins(AssemblyPlugin)
```

Same shape for `persistenceQuill` and `migration`. New `Dependencies.scala` vals
needed (today's `zioTest`/`zioTestSbt` are hardcoded `% Test`, so they can't be
reused as-is for a different configuration):

```scala
val zioTestIT       = "dev.zio" %% "zio-test"     % Versions.zio % IntegrationTest
val zioTestSbtIT     = "dev.zio" %% "zio-test-sbt" % Versions.zio % IntegrationTest
val testcontainersCore     = "org.testcontainers" % "testcontainers" % Versions.testcontainers % IntegrationTest
val testcontainersPostgres = "org.testcontainers" % "postgresql"     % Versions.testcontainers % IntegrationTest
```

**Not evaluated/rejected:** a third-party sbt test-tagging scheme (e.g. custom
`Tags.Tag` + `-l`/`-n` filtering) — this is what ScalaTest projects often reach for,
but it means integration tests still live under the default `Test` configuration
and could accidentally run under `sbt "testOnly *"` if the exclusion tag is ever
forgotten on a new spec. sbt's own `IntegrationTest` config makes the separation
structural instead of tag-based, which is safer against exactly that kind of
mistake.

### Decision 2 — Testcontainers Java directly, no Scala wrapper (recommended)

Use the plain `org.testcontainers:testcontainers` + `org.testcontainers:postgresql`
Java artifacts and wrap the container lifecycle in `ZIO.acquireRelease` /
`ZLayer.scoped` — the same idiom this project already uses for
`QuillDataSourceLayer.live` and `PostgresConfig.transactorLayer`. No
`testcontainers-scala` wrapper dependency:

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

Rejected: a Scala-idiomatic `testcontainers-scala` wrapper library — it adds a
second, less-established dependency with its own ZIO-2/Scala-3 compatibility
surface to track, for a thin lifecycle wrapper this project can write directly in
~10 lines using patterns already established in the codebase. Also rejected:
the `jdbc:tc:postgresql:...` magic JDBC URL prefix (auto-starts a container via
driver-level interception) — it hides container lifecycle from ZIO's resource
management entirely, which fights the `ZLayer.scoped` style used everywhere else
in this codebase.

### Decision 3 — shared container+migration bootstrap: new thin testkit module (recommended)

All three test suites (`migration`, `persistence-postgres`, `persistence-quill`)
need the identical sequence: start a Postgres container → run Flyway against it →
hand back a `DataSource`/JDBC URL. That's genuine logic duplication across three
places, not "three similar lines" — worth a small shared module rather than
copy-pasted per-module setup code.

**Option A (recommended)** — new module `infrastructure/persistence-testkit`,
depended on only via `IntegrationTest`-scoped `dependsOn`
(`persistencePostgres.dependsOn(persistenceTestkit % IntegrationTest)`), so it never
appears on the main compile classpath or in `bootstrap`'s assembly. It depends on
`migration` (for `FlywayMigration`) and exposes one `ZLayer`:
`PostgresContainerLayer: TaskLayer[DataSource]` that starts the container, runs
`Flyway.configure().dataSource(...).load().migrate()` against it, and returns a
`HikariDataSource` pointed at the container. `disablePlugins(AssemblyPlugin)`, no
`coverageSettings` (it's test-support code, not production logic to measure).

**Option B** — duplicate the ~30–40 lines of container+Flyway bootstrap directly
in each of the three modules' `src/it/scala`. Simpler to review in isolation, but
three copies of the same non-trivial lifecycle code that must all stay in sync
(e.g. if the Postgres image tag or Flyway invocation changes).

Recommend Option A given three real consumers of identical setup logic. If this
feels like too much ceremony for what's fundamentally test-only scaffolding, Option
B is the fallback — flagging this as the one structural decision most worth a second
opinion before implementing.

### Decision 4 — naming convention: `*ItSpec`

Existing unit specs use `*Spec` (`AirportEndpointsSpec`, `CountryEndpointsSpec`).
Recommend `*ItSpec` for integration specs — mirrors sbt's own abbreviation for the
`IntegrationTest` configuration (`it:test`), immediately signals "this needs Docker
and a real DB" at a glance, and avoids the ambiguity of `*IntegrationSpec` reading
almost identically to `*Spec` in a directory listing sorted alphabetically.

## Scope — which specs get written

| Module | Spec | Covers |
|---|---|---|
| `infrastructure/migration` | `FlywayMigrationItSpec` | Fresh container, run all `V1`–`V7` migrations via `FlywayMigration.layer`, assert success and that `flyway_schema_history` reaches `V7`. This is the automated version of the manual `\d countries`/`\d airports` check just done by hand — it would have caught the original FK-dependency ordering bug in `V7` (see `plans/surrogate-long-keys-country-airport.md`, Issue 1) without needing manual review. |
| `infrastructure/persistence-postgres` | `DoobieAirportRepositoryItSpec` | The wired, real repository: `save`/`findByIata`/`findAll`/`searchByName`/`findByCountry`/`update`/`delete` round trip against a migrated + seeded container; explicit case for `save` with an unknown `countryCode` asserting `DomainError.CountryNotFound` (the `resolveCountryId` path added in the surrogate-key work) |
| `infrastructure/persistence-postgres` | `DoobieCountryRepositoryItSpec`, `DoobieAirlineRepositoryItSpec`, `DoobieRouteRepositoryItSpec` | Unwired today, but real SQL that should stay correct — would have caught the pre-existing `DoobieAirlineRepository.foundation_date` column mismatch noted during the surrogate-key review immediately, instead of silently at some future wiring point |
| `infrastructure/persistence-quill` | `QuillCountryRepositoryItSpec` | The wired, real repository: `findByCode`/`findAll`/`searchByName`/`save`/`update`/`delete` round trip |

Each spec migrates a **fresh** container per suite (not shared/reused across
suites) — simplest correctness story, avoids state bleeding between specs, and
Testcontainers' container startup (~1–2s for `postgres:16-alpine`) is cheap enough
that this isn't a meaningful runtime cost for an opt-in suite.

## Non-blocking guarantees — explicit checklist

- `sbt compile` — unaffected (`IntegrationTest` sources aren't compiled by `compile`,
  only by `IntegrationTest/compile`).
- `sbt "testOnly *"` — unaffected, different configuration.
- `sbt coverageAggregate` — unaffected; **no `coverageSettings` applied to
  `IntegrationTest`** in any of the three modules (deliberate — see Decision 3's
  testkit module note; keeps the existing CAS-cache-bust dance in `CLAUDE.md`
  scoped to `Test` only, not a fourth configuration to juggle).
- CI workflow (per `CLAUDE.md`'s coverage section: `<module>/testOnly *` per
  module) — unaffected, same reasoning as above.
- Requires Docker running locally to invoke at all (Testcontainers). Not adding
  Docker-availability detection/auto-skip in this plan — out of scope; a developer
  who runs `IntegrationTest/test` without Docker gets a clear container-startup
  failure, which is an acceptable opt-in cost.
- New optional command alias for convenience, alongside the existing `xdup` alias:
  `addCommandAlias("it", "migration/IntegrationTest/test; persistencePostgres/IntegrationTest/test; persistenceQuill/IntegrationTest/test")`.
- **Explicitly out of scope:** wiring these into the CI workflow. The ask here is
  local/manual opt-in via `sbt`; whether CI should ever run a Docker-dependent job
  is a separate decision with its own tradeoffs (CI runner Docker-in-Docker support,
  runtime cost per PR) not addressed by this plan.

## Verification

- `sbt scalafmtAll && sbt compile` — zero warnings, confirms `IntegrationTest`
  sources don't leak into the default `compile` task.
- `sbt "testOnly *"` still passes and still excludes the new specs.
- `sbt "persistencePostgres/IntegrationTest/test"` (and the other two modules) run
  green against a real, freshly-started container.
- `sbt dependencyUpdates` — confirm the Testcontainers version pinned here is still
  the current stable GA at implementation time (per `CLAUDE.md`'s versioning
  policy); this plan intentionally doesn't hardcode a version number for that
  reason.

## Files touched (summary)

- `build.sbt` — `.configs(IntegrationTest)` + `Defaults.itSettings` on `migration`,
  `persistencePostgres`, `persistenceQuill`; new `persistenceTestkit` module
  (Decision 3, Option A); new `it` command alias
- `project/Dependencies.scala` — `Versions.testcontainers`, `zioTestIT`,
  `zioTestSbtIT`, `testcontainersCore`, `testcontainersPostgres`
- `infrastructure/persistence-testkit/src/main/scala/.../PostgresContainerLayer.scala` (new module)
- `infrastructure/migration/src/it/scala/.../FlywayMigrationItSpec.scala` (new)
- `infrastructure/persistence-postgres/src/it/scala/.../DoobieAirportRepositoryItSpec.scala` (new)
- `infrastructure/persistence-postgres/src/it/scala/.../DoobieCountryRepositoryItSpec.scala` (new)
- `infrastructure/persistence-postgres/src/it/scala/.../DoobieAirlineRepositoryItSpec.scala` (new)
- `infrastructure/persistence-postgres/src/it/scala/.../DoobieRouteRepositoryItSpec.scala` (new)
- `infrastructure/persistence-quill/src/it/scala/.../QuillCountryRepositoryItSpec.scala` (new)
- `CLAUDE.md` — document the `sbt "<module>/IntegrationTest/test"` / `sbt it`
  workflow as opt-in, Docker-dependent, and separate from the `testOnly *`/coverage
  workflow already documented
