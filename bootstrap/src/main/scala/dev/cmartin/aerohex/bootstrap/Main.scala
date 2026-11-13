package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.infrastructure.migration.FlywayMigration
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.PostgresConfig
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  // Every ZIO.config call in the app reads application.conf, whose ${?ENV_VAR} substitutions keep
  // the documented env vars working (plans/configuration-zio-config.md).
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] = (Runtime.removeDefaultLoggers >>> SLF4J.slf4j) ++
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  // Migrations run in-process before the server binds, so the app never serves against a schema
  // it hasn't verified. On by default: dev/local is the only environment today, and an opt-in
  // default would reintroduce the manual migration step this exists to remove (see
  // plans/run-flyway-on-startup.md). Disable with FLYWAY_MIGRATE_ON_START=false.
  private val migrateIfEnabled: Task[Unit] =
    ZIO.config(Config.boolean("migrate-on-start").withDefault(true).nested("flyway")).flatMap {
      case false => ZIO.logInfo("Flyway: skipped (flyway.migrate-on-start=false)")
      case true  =>
        ZIO.config(PostgresConfig.config).flatMap(db => FlywayMigration.migrate(db.url, db.user, db.passwordValue))
    }

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("Aviation Hexagonal starting (API-dev mode)") *>
      migrateIfEnabled *>
      HttpServer.serve.provide(WiringModule.appLayer)
}
