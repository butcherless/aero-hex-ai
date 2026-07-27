package dev.cmartin.aerohex.application.auth

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{AccessToken, LogoutUseCase, TokenService, ValidatedToken}
import java.time.Instant
import zio.test.*
import zio.{IO, Ref, Scope, UIO, ZIO, ZLayer}

object LogoutServiceSpec extends ZIOSpecDefault:

  private def recordingTokenService(calls: Ref[List[(String, Instant)]]): TokenService = new TokenService:
    def generate(username: String): UIO[AccessToken]             = ZIO.die(new NotImplementedError("generate"))
    def validate(token: String): IO[DomainError, ValidatedToken] = ZIO.die(new NotImplementedError("validate"))
    def revoke(jti: String, expiresAt: Instant): UIO[Unit]       = calls.update(_ :+ (jti -> expiresAt))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("LogoutService")(
      test("delegates to TokenService.revoke with the exact jti and expiresAt passed in") {
        val expiresAt = Instant.parse("2026-01-01T01:00:00Z")
        for
          calls    <- Ref.make(List.empty[(String, Instant)])
          service   = new LogoutService(recordingTokenService(calls))
          _        <- service.logout("some-jti", expiresAt)
          recorded <- calls.get
        yield assertTrue(recorded == List("some-jti" -> expiresAt))
      },
      test("LogoutService.layer constructs a usable instance") {
        for
          calls <- Ref.make(List.empty[(String, Instant)])
          _     <- ZIO
                     .service[LogoutUseCase]
                     .provide(ZLayer.succeed(recordingTokenService(calls)), LogoutService.layer)
        yield assertCompletes
      }
    )
