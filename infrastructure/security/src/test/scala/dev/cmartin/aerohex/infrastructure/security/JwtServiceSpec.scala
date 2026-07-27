package dev.cmartin.aerohex.infrastructure.security

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.RevokedTokenRepository
import java.time.{Clock, Instant, ZoneOffset}
import zio.test.*
import zio.{Scope, UIO, ZIO}

object JwtServiceSpec extends ZIOSpecDefault:

  private val config =
    JwtConfig(secretKey = "test-secret", ttlSeconds = 60, issuer = "test-issuer", audience = "test-audience")

  private def clockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)

  // In-memory fake, not a stub-with-defaults-and-overrides like the persistence-layer
  // repository stubs elsewhere — there's no "unimplemented dies loudly" need here, every test
  // in this file exercises revocation through the real isRevoked/revoke round-trip.
  private def fakeRevokedTokenRepo(): RevokedTokenRepository = new RevokedTokenRepository:
    private val revoked                                             = scala.collection.mutable.Set.empty[String]
    override def isRevoked(jti: String): UIO[Boolean]               = ZIO.succeed(revoked.contains(jti))
    override def revoke(jti: String, expiresAt: Instant): UIO[Unit] = ZIO.succeed(revoked.add(jti)).unit

  private val issuedAt = Instant.parse("2026-01-01T00:00:00Z")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JwtService")(
      test("generate then validate round-trips to the original username, with the configured ttl") {
        val service = new JwtService(config, fakeRevokedTokenRepo(), clockAt(issuedAt))
        for
          token  <- service.generate("alice")
          result <- service.validate(token.value)
        yield assertTrue(result.username == "alice", token.expiresInSeconds == 60)
      },
      test("validate fails with TokenExpired once the token's ttl has elapsed") {
        val repo    = fakeRevokedTokenRepo()
        val issuing = new JwtService(config, repo, clockAt(issuedAt))
        val later   = new JwtService(config, repo, clockAt(issuedAt.plusSeconds(61)))
        for
          token <- issuing.generate("alice")
          error <- later.validate(token.value).flip
        yield assertTrue(error == DomainError.TokenExpired)
      },
      test("validate succeeds one second before the token expires") {
        val repo    = fakeRevokedTokenRepo()
        val issuing = new JwtService(config, repo, clockAt(issuedAt))
        val almost  = new JwtService(config, repo, clockAt(issuedAt.plusSeconds(59)))
        for
          token  <- issuing.generate("alice")
          result <- almost.validate(token.value)
        yield assertTrue(result.username == "alice")
      },
      test("validate fails for a token signed with a different secret") {
        val repo    = fakeRevokedTokenRepo()
        val issuing = new JwtService(config.copy(secretKey = "other-secret"), repo, clockAt(issuedAt))
        val verify  = new JwtService(config, repo, clockAt(issuedAt))
        for
          token <- issuing.generate("alice")
          error <- verify.validate(token.value).flip
        yield assertTrue(error.isInstanceOf[DomainError.InvalidToken])
      },
      test("validate fails when the audience does not match") {
        val repo    = fakeRevokedTokenRepo()
        val issuing = new JwtService(config.copy(audience = "different-audience"), repo, clockAt(issuedAt))
        val verify  = new JwtService(config, repo, clockAt(issuedAt))
        for
          token <- issuing.generate("alice")
          error <- verify.validate(token.value).flip
        yield assertTrue(error == DomainError.InvalidToken("issuer or audience mismatch"))
      },
      test("validate fails with TokenRevoked after revoke() is called for that jti") {
        val repo    = fakeRevokedTokenRepo()
        val service = new JwtService(config, repo, clockAt(issuedAt))
        for
          token  <- service.generate("alice")
          result <- service.validate(token.value)
          _      <- service.revoke(result.jti, result.expiresAt)
          error  <- service.validate(token.value).flip
        yield assertTrue(error == DomainError.TokenRevoked)
      },
      test("revoke is idempotent — revoking an already-revoked jti is not an error") {
        val repo    = fakeRevokedTokenRepo()
        val service = new JwtService(config, repo, clockAt(issuedAt))
        for
          token  <- service.generate("alice")
          result <- service.validate(token.value)
          _      <- service.revoke(result.jti, result.expiresAt)
          _      <- service.revoke(result.jti, result.expiresAt)
          error  <- service.validate(token.value).flip
        yield assertTrue(error == DomainError.TokenRevoked)
      }
    )
