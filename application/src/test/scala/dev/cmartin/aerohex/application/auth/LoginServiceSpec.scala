package dev.cmartin.aerohex.application.auth

import dev.cmartin.aerohex.application.support.ServiceLayerSpecSupport.constructsUsableInstance
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{
  AccessToken,
  LoginUseCase,
  PasswordHasher,
  TokenService,
  User,
  UserRepository,
  ValidatedToken
}
import java.time.Instant
import zio.test.*
import zio.{IO, Ref, Scope, UIO, ZIO, ZLayer}

object LoginServiceSpec extends ZIOSpecDefault:

  private val alice = User("alice", "hashed-password")

  private def repoReturning(user: Option[User]): UserRepository =
    (_: String) => ZIO.succeed(user)

  private def hasherReturning(valid: Boolean): PasswordHasher = new PasswordHasher:
    def verify(plain: String, hash: String): UIO[Boolean] = ZIO.succeed(valid)
    def simulateVerify(plain: String): UIO[Unit]          = ZIO.unit

  private val issuingTokenService: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             =
      ZIO.succeed(AccessToken(s"token-for-$username", 3600))
    def validate(token: String): IO[DomainError, ValidatedToken] = ZIO.die(new NotImplementedError("validate"))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = ZIO.die(new NotImplementedError("revoke"))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("LoginService")(
      test("returns a token when the username exists and the password matches") {
        val service = new LoginService(repoReturning(Some(alice)), hasherReturning(true), issuingTokenService)
        for result <- service.login("alice", "correct-password")
        yield assertTrue(result == AccessToken("token-for-alice", 3600))
      },
      test("fails with InvalidCredentials when the username does not exist, after a simulated hash check") {
        for
          simulated <- Ref.make(List.empty[String])
          hasher     = new PasswordHasher:
                         def verify(plain: String, hash: String): UIO[Boolean] =
                           ZIO.die(new NotImplementedError("verify"))
                         def simulateVerify(plain: String): UIO[Unit]          = simulated.update(plain :: _)
          service    = new LoginService(repoReturning(None), hasher, issuingTokenService)
          error     <- service.login("nobody", "whatever").flip
          calls     <- simulated.get
        yield assertTrue(error == DomainError.InvalidCredentials, calls == List("whatever"))
      },
      test("fails with InvalidCredentials (the same error as an unknown username) when the password does not match") {
        val service = new LoginService(repoReturning(Some(alice)), hasherReturning(false), issuingTokenService)
        for error <- service.login("alice", "wrong-password").flip
        yield assertTrue(error == DomainError.InvalidCredentials)
      },
      constructsUsableInstance("LoginService")(
        ZIO
          .service[LoginUseCase]
          .provide(
            ZLayer.succeed(repoReturning(None)),
            ZLayer.succeed(hasherReturning(false)),
            ZLayer.succeed(issuingTokenService),
            LoginService.layer
          )
      )
    )
