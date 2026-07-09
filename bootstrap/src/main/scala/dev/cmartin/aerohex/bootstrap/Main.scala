package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.infrastructure.migration.FlywayMigration
import zio.*
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  // Migrations run in-process before the server binds, so the app never serves against a schema
  // it hasn't verified. Anything but an explicit "false" means on: dev/local is the only
  // environment today, and an opt-in default would reintroduce the manual migration step this
  // exists to remove (see plans/run-flyway-on-startup.md).
  private val migrateIfEnabled: Task[Unit] =
    if (sys.env.get("FLYWAY_MIGRATE_ON_START").exists(_.equalsIgnoreCase("false")))
      ZIO.logInfo("Flyway: skipped (FLYWAY_MIGRATE_ON_START=false)")
    else
      FlywayMigration.migrateFromEnv

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.logInfo("Aviation Hexagonal starting (API-dev mode)") *>
      migrateIfEnabled *>
      HttpServer.serve.provide(WiringModule.appLayer)
}
