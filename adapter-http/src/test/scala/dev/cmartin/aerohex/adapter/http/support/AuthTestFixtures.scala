package dev.cmartin.aerohex.adapter.http.support

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, TokenService, ValidatedToken}
import java.time.Instant
import sttp.client4.*
import sttp.model.{StatusCode, Uri}
import zio.test.*
import zio.{IO, Task, UIO, ZIO}

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

  // The "missing header" / "rejected token" pair every protected endpoint's spec repeats,
  // parametrized only by the target URI and how the spec's own makeBackend threads a
  // TokenService in.
  def authenticationSuite(target: Uri)(makeBackend: TokenService => Backend[Task]): Spec[Any, Any] =
    suite("Authentication")(
      test("returns 401 when the Authorization header is missing") {
        for response <- basicRequest.get(target).send(makeBackend(validToken))
        yield assertTrue(response.code == StatusCode.Unauthorized)
      },
      test("returns 401 when the token is rejected") {
        for response <- authedRequest.get(target).send(makeBackend(rejectingToken))
        yield assertTrue(response.code == StatusCode.Unauthorized)
      }
    )
