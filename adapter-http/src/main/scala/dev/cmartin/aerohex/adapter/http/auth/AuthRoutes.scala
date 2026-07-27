package dev.cmartin.aerohex.adapter.http.auth

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.user.{LoginUseCase, LogoutUseCase, TokenService}
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AuthRoutes(loginUseCase: LoginUseCase, logoutUseCase: LogoutUseCase, tokenService: TokenService):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AuthEndpoints.login.zServerLogic { req =>
      loginUseCase
        .login(req.username, req.password)
        .map(token => TokenResponse(token.value, "Bearer", token.expiresInSeconds))
        .mapError(ErrorMapper.toHttpError)
    },
    AuthEndpoints.logout.zServerSecurityLogic(secured).serverLogic { validated => _ =>
      logoutUseCase.logout(validated.jti, validated.expiresAt)
    }
  )

object AuthRoutes:
  val layer: URLayer[LoginUseCase & LogoutUseCase & TokenService, AuthRoutes] =
    ZLayer.fromFunction(new AuthRoutes(_, _, _))
