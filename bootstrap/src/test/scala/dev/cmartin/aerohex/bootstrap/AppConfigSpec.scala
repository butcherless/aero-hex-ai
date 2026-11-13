package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.server.HttpConfig
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.PostgresConfig
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*
import zio.{Config, Scope, ZIO}

// Loads the real application.conf through the same provider Main installs, so a typo in a key
// name or a missing section fails here, not at startup.
object AppConfigSpec extends ZIOSpecDefault:

  private val provider = TypesafeConfigProvider.fromResourcePath()

  private def load[A](config: Config[A]) = ZIO.config(config).withConfigProvider(provider)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("application.conf")(
      test("provides the postgres settings") {
        for db <- load(PostgresConfig.config)
        yield assertTrue(db.url.startsWith("jdbc:postgresql://"), db.user.nonEmpty, db.maxPoolSize > 0)
      },
      test("provides the http port") {
        for http <- load(HttpConfig.config)
        yield assertTrue(http.port > 0)
      },
      test("provides the flyway flag") {
        // No default here, unlike Main's: this checks the key is present in the file and is a valid
        // boolean. The value itself depends on FLYWAY_MIGRATE_ON_START in the test environment.
        for exit <- load(Config.boolean("migrate-on-start").nested("flyway")).exit
        yield assertTrue(exit.isSuccess)
      }
    )
