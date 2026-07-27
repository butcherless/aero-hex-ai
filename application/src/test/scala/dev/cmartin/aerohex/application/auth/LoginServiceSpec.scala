package dev.cmartin.aerohex.application.auth

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, LoginUseCase, PasswordHasher, TokenService, User, UserRepository}
import zio.test.*
import zio.{IO, Scope, UIO, ZIO, ZLayer}

object LoginServiceSpec extends ZIOSpecDefault:

  private val alice = User("alice", "hashed-password")

  private def repoReturning(user: Option[User]): UserRepository =
    (_: String) => ZIO.succeed(user)

  private def hasherReturning(valid: Boolean): PasswordHasher =
    (_: String, _: String) => ZIO.succeed(valid)

  private val issuingTokenService: TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]     = ZIO.succeed(AccessToken(s"token-for-$username", 3600))
    def validate(token: String): IO[DomainError, String] = ZIO.die(new NotImplementedError("validate"))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("LoginService")(
      test("returns a token when the username exists and the password matches") {
        val service = new LoginService(repoReturning(Some(alice)), hasherReturning(true), issuingTokenService)
        for result <- service.login("alice", "correct-password")
        yield assertTrue(result == AccessToken("token-for-alice", 3600))
      },
      test("fails with InvalidCredentials when the username does not exist, and never calls the password hasher") {
        val service =
          new LoginService(
            repoReturning(None),
            (_: String, _: String) => ZIO.die(new NotImplementedError("verify")),
            issuingTokenService
          )
        for error <- service.login("nobody", "whatever").flip
        yield assertTrue(error == DomainError.InvalidCredentials)
      },
      test("fails with InvalidCredentials (the same error as an unknown username) when the password does not match") {
        val service = new LoginService(repoReturning(Some(alice)), hasherReturning(false), issuingTokenService)
        for error <- service.login("alice", "wrong-password").flip
        yield assertTrue(error == DomainError.InvalidCredentials)
      },
      test("LoginService.layer constructs a usable instance") {
        for _ <- ZIO
                   .service[LoginUseCase]
                   .provide(
                     ZLayer.succeed(repoReturning(None)),
                     ZLayer.succeed(hasherReturning(false)),
                     ZLayer.succeed(issuingTokenService),
                     LoginService.layer
                   )
        yield assertCompletes
      }
    )
