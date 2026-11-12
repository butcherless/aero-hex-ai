package dev.cmartin.aerohex.infrastructure.security

import zio.Scope
import zio.test.*

object JwtConfigSpec extends ZIOSpecDefault:

  private def envOf(pairs: (String, String)*): String => Option[String] = pairs.toMap.get

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JwtConfig.fromEnv")(
      test("uses JWT_SECRET_KEY when it is at least 32 bytes") {
        val secret = "x" * JwtConfig.MinSecretBytes
        for config <- JwtConfig.fromEnv(envOf("JWT_SECRET_KEY" -> secret))
        yield assertTrue(config.secretKey == secret)
      },
      test("fails when JWT_SECRET_KEY is shorter than 32 bytes") {
        for result <- JwtConfig.fromEnv(envOf("JWT_SECRET_KEY" -> "dev-only-change-me")).either
        yield assertTrue(result.isLeft)
      },
      test("generates a different random secret per call when JWT_SECRET_KEY is unset") {
        for
          first  <- JwtConfig.fromEnv(envOf())
          second <- JwtConfig.fromEnv(envOf())
        yield assertTrue(first.secretKey.nonEmpty, first.secretKey != second.secretKey)
      },
      test("keeps the defaults for the other settings") {
        for config <- JwtConfig.fromEnv(envOf())
        yield assertTrue(
          config.ttlSeconds == 3600,
          config.issuer == "aero-hex-ai",
          config.audience == "aero-hex-ai-api"
        )
      }
    )
