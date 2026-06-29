package dev.cmartin.aerohex.infrastructure.persistence.quill.config

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import zio.{TaskLayer, ZIO, ZLayer}

import javax.sql.DataSource

object QuillDataSourceLayer {

  private case class DbConfig(url: String, user: String, password: String)

  private val dbConfig = DbConfig(
    url = sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://localhost:5432/aviation"),
    user = sys.env.getOrElse("POSTGRES_USER", "aviation"),
    password = sys.env.getOrElse("POSTGRES_PASSWORD", "aviation")
  )

  val live: TaskLayer[DataSource] = ZLayer.scoped {
    ZIO.acquireRelease(
      ZIO.attempt {
        val cfg = new HikariConfig()
        cfg.setJdbcUrl(dbConfig.url)
        cfg.setUsername(dbConfig.user)
        cfg.setPassword(dbConfig.password)
        cfg.setMaximumPoolSize(10)
        new HikariDataSource(cfg)
      }
    )(ds => ZIO.attempt(ds.close()).ignoreLogged)
  }
}
