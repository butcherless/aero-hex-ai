package dev.cmartin.aerohex.adapter.http.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, TokenService, ValidatedToken}
import java.time.Instant
import sttp.client4.*
import zio.{IO, UIO, ZIO}

// Stand-ins for the real JwtService shared by every protected-endpoint spec: validToken always
// succeeds, rejectingToken always fails, regardless of the token string actually sent (that
// string-vs-signature distinction is JwtServiceSpec's job, not these specs').
trait AuthTestFixtures:

  val validToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, ValidatedToken] =
      ZIO.succeed(ValidatedToken("test-user", "test-jti", Instant.parse("2026-01-01T01:00:00Z")))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = ZIO.die(new NotImplementedError("revoke"))

  val rejectingToken: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, ValidatedToken] = ZIO.fail(DomainError.InvalidToken("rejected"))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = ZIO.die(new NotImplementedError("revoke"))

  val authedRequest = basicRequest.header("Authorization", "Bearer test-token")
