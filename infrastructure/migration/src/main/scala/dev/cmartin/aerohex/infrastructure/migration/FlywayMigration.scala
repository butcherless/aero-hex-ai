package dev.cmartin.aerohex.infrastructure.migration

import org.flywaydb.core.Flyway
import zio.*

// Connection settings are passed in by the caller (bootstrap's Main reads them through ZIO
// Config, see plans/configuration-zio-config.md), so this module stays free of config concerns.
object FlywayMigration {

  def migrate(url: String, user: String, password: String): Task[Unit] =
    ZIO.attemptBlocking {
      val result = Flyway
        .configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .load()
        .migrate()
      ZIO.logInfo(s"Flyway: applied ${result.migrationsExecuted} migration(s)")
    }.flatten
}
