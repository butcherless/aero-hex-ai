package dev.cmartin.aerohex.application.auth

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.user.{LogoutUseCase, TokenService}
import java.time.Instant
import zio.{UIO, URLayer, ZLayer}

final class LogoutService(tokenService: TokenService) extends LogoutUseCase {
  override def logout(jti: String, expiresAt: Instant): UIO[Unit] =
    tokenService.revoke(jti, expiresAt) @@ ServiceAspect.logged(s"LogoutService.logout($jti)")
}

object LogoutService {
  val layer: URLayer[TokenService, LogoutUseCase] =
    ZLayer.fromFunction(new LogoutService(_))
}
