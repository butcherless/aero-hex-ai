package dev.cmartin.aerohex.infrastructure.security

import zio.test.*
import zio.{ConfigProvider, Scope}

object JwtConfigSpec extends ZIOSpecDefault:

  // Same key layout as application.conf (jwt.secret-key, ...).
  private def loadWith(pairs: (String, String)*) =
    JwtConfig.load.withConfigProvider(ConfigProvider.fromMap(pairs.toMap))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JwtConfig.load")(
      test("uses jwt.secret-key when it is at least 32 bytes") {
        val secret = "x" * JwtConfig.MinSecretBytes
        for config <- loadWith("jwt.secret-key" -> secret)
        yield assertTrue(config.secretKey == secret)
      },
      test("fails when jwt.secret-key is shorter than 32 bytes") {
        for result <- loadWith("jwt.secret-key" -> "dev-only-change-me").either
        yield assertTrue(result.isLeft)
      },
      test("generates a different random secret per load when jwt.secret-key is unset") {
        for
          first  <- loadWith()
          second <- loadWith()
        yield assertTrue(first.secretKey.nonEmpty, first.secretKey != second.secretKey)
      },
      test("uses the defaults for the other settings when unset") {
        for config <- loadWith()
        yield assertTrue(
          config.ttlSeconds == 3600,
          config.issuer == "aero-hex-ai",
          config.audience == "aero-hex-ai-api"
        )
      },
      test("reads the other settings when set") {
        for config <- loadWith("jwt.ttl-seconds" -> "60", "jwt.issuer" -> "i", "jwt.audience" -> "a")
        yield assertTrue(config.ttlSeconds == 60, config.issuer == "i", config.audience == "a")
      }
    )
