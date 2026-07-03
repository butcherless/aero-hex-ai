package dev.cmartin.aerohex.infrastructure.persistence.postgres.config

import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import zio.interop.catz.*
import zio.{Task, TaskLayer, ZLayer}

case class PostgresConfig(
    url: String,
    user: String,
    password: String,
    maxPoolSize: Int
)

object PostgresConfig {

  val default: PostgresConfig = PostgresConfig(
    url = sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://localhost:5432/aviation"),
    user = sys.env.getOrElse("POSTGRES_USER", "aviation"),
    password = sys.env.getOrElse("POSTGRES_PASSWORD", "aviation"),
    maxPoolSize = 10
  )

  val transactorLayer: TaskLayer[Transactor[Task]] = ZLayer.scoped {
    val hikariConfig = new HikariConfig()
    hikariConfig.setDriverClassName("org.postgresql.Driver")
    hikariConfig.setJdbcUrl(default.url)
    hikariConfig.setUsername(default.user)
    hikariConfig.setPassword(default.password)
    hikariConfig.setMaximumPoolSize(default.maxPoolSize)

    HikariTransactor.fromHikariConfig[Task](hikariConfig).toScopedZIO
  }
}
