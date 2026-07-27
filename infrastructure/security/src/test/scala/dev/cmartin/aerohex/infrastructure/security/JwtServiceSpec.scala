package dev.cmartin.aerohex.infrastructure.security

import dev.cmartin.aerohex.domain.error.DomainError
import java.time.{Clock, Instant, ZoneOffset}
import zio.Scope
import zio.test.*

object JwtServiceSpec extends ZIOSpecDefault:

  private val config =
    JwtConfig(secretKey = "test-secret", ttlSeconds = 60, issuer = "test-issuer", audience = "test-audience")

  private def clockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

  private val issuedAt = Instant.parse("2026-01-01T00:00:00Z")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JwtService")(
      test("generate then validate round-trips to the original username, with the configured ttl") {
        val service = new JwtService(config, clockAt(issuedAt))
        for
          token    <- service.generate("alice")
          username <- service.validate(token.value)
        yield assertTrue(username == "alice", token.expiresInSeconds == 60)
      },
      test("validate fails with TokenExpired once the token's ttl has elapsed") {
        val issuing = new JwtService(config, clockAt(issuedAt))
        val later   = new JwtService(config, clockAt(issuedAt.plusSeconds(61)))
        for
          token <- issuing.generate("alice")
          error <- later.validate(token.value).flip
        yield assertTrue(error == DomainError.TokenExpired)
      },
      test("validate succeeds one second before the token expires") {
        val issuing = new JwtService(config, clockAt(issuedAt))
        val almost  = new JwtService(config, clockAt(issuedAt.plusSeconds(59)))
        for
          token    <- issuing.generate("alice")
          username <- almost.validate(token.value)
        yield assertTrue(username == "alice")
      },
      test("validate fails for a token signed with a different secret") {
        val issuing = new JwtService(config.copy(secretKey = "other-secret"), clockAt(issuedAt))
        val verify  = new JwtService(config, clockAt(issuedAt))
        for
          token <- issuing.generate("alice")
          error <- verify.validate(token.value).flip
        yield assertTrue(error.isInstanceOf[DomainError.InvalidToken])
      },
      test("validate fails when the audience does not match") {
        val issuing = new JwtService(config.copy(audience = "different-audience"), clockAt(issuedAt))
        val verify  = new JwtService(config, clockAt(issuedAt))
        for
          token <- issuing.generate("alice")
          error <- verify.validate(token.value).flip
        yield assertTrue(error == DomainError.InvalidToken("issuer or audience mismatch"))
      }
    )
