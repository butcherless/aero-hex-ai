package dev.cmartin.aerohex.adapter.http.common

import dev.cmartin.aerohex.adapter.http.error.{ErrorMapper, HttpErrorResponse}
import dev.cmartin.aerohex.domain.user.TokenService
import sttp.model.StatusCode
import zio.IO

// Reused by every resource's XxxRoutes (plans/security/protect-endpoints.md decision 2) so the
// TokenService.validate -> ErrorMapper wiring exists in exactly one place instead of once per
// resource.
object SecuredEndpoint:
  def securityLogic(tokenService: TokenService)(token: String): IO[(StatusCode, HttpErrorResponse), String] =
    tokenService.validate(token).mapError(ErrorMapper.toHttpError)
