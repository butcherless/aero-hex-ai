# Plan: load configuration through ZIO Config

> **Status:** Implemented (2026-11-13).

## Goal

Before this change, configuration was read with `sys.env` in six places: `HttpServer.port`,
`JwtConfig`, `QuillDataSourceLayer`, `FlywayMigration.migrateFromEnv`, `KafkaConfig` and
`Main`'s Flyway flag. Each repeated its own defaults. `bootstrap/src/main/resources/application.conf`
existed, but nothing read it; it was documentation only, and it had already drifted
(`kafka.group-id` had no env override). The pool size was hardcoded to 10 despite
`postgres.max-pool-size` in the file. Values read at object initialization also can't be
replaced in tests.

## Decisions

### 1. ZIO core `Config` in each module; `zio-config-typesafe` only in `bootstrap`

Options checked, in the `choose-library` skill's order:

| Option | Verdict |
|---|---|
| ZIO core `zio.Config` / `ConfigProvider` / `ZIO.config` | **Chosen for describing config.** Already a dependency of every module, so no new dependency in `persistence-quill`, `security`, `adapter-http`, `messaging-kafka`. |
| `zio-config-typesafe` 4.1.2 (official ZIO ecosystem) | **Chosen for reading `application.conf`.** Supplies `TypesafeConfigProvider.fromResourcePath()`. Added to `bootstrap` only, the composition root. |
| `zio-config-magnolia` (`deriveConfig`) | Rejected for now. Each config has 1–4 fields, so hand-written `Config` values are short and make key names explicit. |
| PureConfig / Ciris | Rejected. Not ZIO ecosystem, and ZIO core already covers it. |

Each infrastructure module keeps its own small config type next to the code that uses it
(`PostgresConfig` in persistence-quill, `JwtConfig` in security, `HttpConfig` in adapter-http,
`KafkaConfig` in messaging-kafka). Each exposes a `zio.Config` with the same key paths as
`application.conf`. No module reads `sys.env` directly.

### 2. Provider set once, in `Main.bootstrap`

`Main` installs `TypesafeConfigProvider.fromResourcePath()`. `application.conf`'s
`${?ENV_VAR}` substitutions keep every existing env var working. Entry points without
`application.conf` (master-data-sync, tests) keep ZIO's default provider, which maps
`Config.string("url").nested("postgres")` to the `POSTGRES_URL` env var. Each config also has
`withDefault`s that match the old fallbacks, so those entry points behave as before.

### 3. Secrets as `Config.Secret`

The Postgres password and JWT secret are read with `Config.secret`, so they never appear in
`toString` or logs. The JWT rules from `plans/security/login.md` (2026-11-13 revision) stay
the same: unset means a random per-process secret, and shorter than 32 bytes aborts startup,
now as a `Config.Error`.

### 4. Flyway

`FlywayMigration.migrateFromEnv` and its unused `layer` are removed. `Main` reads
`PostgresConfig` and `flyway.migrate-on-start` (env `FLYWAY_MIGRATE_ON_START`), then calls
`FlywayMigration.migrate(url, user, password)`. Behaviour change: the flag must now be a valid
boolean. Before, any value other than `false` meant "on"; now an invalid value is a startup
error.

## Files touched

`build.sbt`, `project/{Versions,Dependencies}.scala`, `application.conf`, `Main.scala`,
`QuillDataSourceLayer.scala` (+ new `PostgresConfig.scala`), `FlywayMigration.scala`,
`JwtConfig.scala` (+ spec), `HttpServer.scala` (+ new `HttpConfig.scala`), `KafkaConfig.scala`,
`RouteEventProducer.scala`, new `bootstrap` `AppConfigSpec`, CLAUDE.md.
