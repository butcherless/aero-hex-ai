package dev.cmartin.aerohex.adapter.http.auth

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.user.LoginUseCase
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AuthRoutes(loginUseCase: LoginUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AuthEndpoints.login.zServerLogic { req =>
      loginUseCase
        .login(req.username, req.password)
        .map(token => TokenResponse(token.value, "Bearer", token.expiresInSeconds))
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AuthRoutes:
  val layer: URLayer[LoginUseCase, AuthRoutes] =
    ZLayer.fromFunction(new AuthRoutes(_))
