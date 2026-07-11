package dev.cmartin.aerohex.it.migration

import dev.cmartin.aerohex.infrastructure.migration.FlywayMigration
import dev.cmartin.aerohex.it.support.PostgresContainerSupport
import org.testcontainers.containers.PostgreSQLContainer
import zio.*
import zio.test.*

import java.sql.DriverManager

object FlywayMigrationItSpec extends ZIOSpecDefault {

  private def latestSchemaVersion(container: PostgreSQLContainer[?]): Task[String] =
    ZIO.attemptBlocking {
      val conn = DriverManager.getConnection(container.getJdbcUrl, container.getUsername, container.getPassword)
      try {
        val rs = conn
          .createStatement()
          .executeQuery("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
        rs.next()
        rs.getString("version")
      } finally conn.close()
    }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FlywayMigration")(
      test("applies all migrations up to V11 against a fresh container") {
        for
          container <- ZIO.service[PostgreSQLContainer[?]]
          _         <- FlywayMigration.migrate(container.getJdbcUrl, container.getUsername, container.getPassword)
          version   <- latestSchemaVersion(container)
        yield assertTrue(version == "11")
      }
    ).provideLayerShared(PostgresContainerSupport.containerLayer)
}
