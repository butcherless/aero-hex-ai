package dev.cmartin.aerohex.infrastructure.persistence.quill.config

import zio.Config

/** Connection settings for the shared Postgres pool. Keys live under `postgres`
  * (`postgres.url` in `application.conf`, `POSTGRES_URL` with ZIO's default env
  * provider). The defaults match the local docker-compose database.
  */
final case class PostgresConfig(url: String, user: String, password: Config.Secret, maxPoolSize: Int):
  def passwordValue: String = new String(password.value.toArray)

object PostgresConfig:
  val config: Config[PostgresConfig] =
    (Config.string("url").withDefault("jdbc:postgresql://localhost:5432/aviation") ++
      Config.string("user").withDefault("aviation") ++
      Config.secret("password").withDefault(Config.Secret("aviation")) ++
      Config.int("max-pool-size").withDefault(10))
      .map(PostgresConfig.apply)
      .nested("postgres")
