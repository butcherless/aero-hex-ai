package dev.cmartin.aerohex.it.support

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import dev.cmartin.aerohex.infrastructure.migration.FlywayMigration
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import javax.sql.DataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio.interop.catz.*
import zio.{Task, TaskLayer, ZIO, ZLayer}

object PostgresContainerSupport {

  private val image = DockerImageName.parse("postgres:18-alpine")

  // Fresh container per suite: simplest correctness story, avoids state bleeding
  // between specs. See plans/add-persistence-integration-tests.md.
  val containerLayer: TaskLayer[PostgreSQLContainer[?]] = ZLayer.scoped {
    ZIO.acquireRelease(
      ZIO.attempt {
        val c = new PostgreSQLContainer(image)
        c.start()
        c
      }
    )(c => ZIO.attempt(c.stop()).ignoreLogged)
  }

  val migratedContainerLayer: TaskLayer[PostgreSQLContainer[?]] =
    containerLayer.tap { env =>
      val c = env.get[PostgreSQLContainer[?]]
      FlywayMigration.migrate(c.getJdbcUrl, c.getUsername, c.getPassword)
    }

  private def buildDataSource(c: PostgreSQLContainer[?]): HikariDataSource = {
    val cfg = new HikariConfig()
    cfg.setJdbcUrl(c.getJdbcUrl)
    cfg.setUsername(c.getUsername)
    cfg.setPassword(c.getPassword)
    cfg.setMaximumPoolSize(5)
    new HikariDataSource(cfg)
  }

  private val dataSourceFromContainer: ZLayer[PostgreSQLContainer[?], Throwable, DataSource] =
    ZLayer.scoped {
      for
        c  <- ZIO.service[PostgreSQLContainer[?]]
        ds <- ZIO.acquireRelease(ZIO.attempt(buildDataSource(c)))(ds => ZIO.attempt(ds.close()).ignoreLogged)
      yield ds: DataSource
    }

  val dataSourceLayer: TaskLayer[DataSource] = migratedContainerLayer >>> dataSourceFromContainer

  private val transactorFromContainer: ZLayer[PostgreSQLContainer[?], Throwable, Transactor[Task]] =
    ZLayer.scoped {
      ZIO.service[PostgreSQLContainer[?]].flatMap { c =>
        val hikariConfig = new HikariConfig()
        hikariConfig.setDriverClassName("org.postgresql.Driver")
        hikariConfig.setJdbcUrl(c.getJdbcUrl)
        hikariConfig.setUsername(c.getUsername)
        hikariConfig.setPassword(c.getPassword)
        hikariConfig.setMaximumPoolSize(5)
        HikariTransactor.fromHikariConfig[Task](hikariConfig).toScopedZIO
      }
    }

  val transactorLayer: TaskLayer[Transactor[Task]] = migratedContainerLayer >>> transactorFromContainer
}
