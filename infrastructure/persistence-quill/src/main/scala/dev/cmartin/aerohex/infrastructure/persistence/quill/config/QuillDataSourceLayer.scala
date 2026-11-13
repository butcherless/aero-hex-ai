package dev.cmartin.aerohex.infrastructure.persistence.quill.config

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
import zio.{TaskLayer, ZIO, ZLayer}

object QuillDataSourceLayer {

  val live: TaskLayer[DataSource] = ZLayer.scoped {
    ZIO.config(PostgresConfig.config).flatMap { db =>
      ZIO.acquireRelease(
        ZIO.attempt {
          val cfg = new HikariConfig()
          cfg.setJdbcUrl(db.url)
          cfg.setUsername(db.user)
          cfg.setPassword(db.passwordValue)
          cfg.setMaximumPoolSize(db.maxPoolSize)
          new HikariDataSource(cfg)
        }
      )(ds => ZIO.attempt(ds.close()).ignoreLogged)
    }
  }
}
