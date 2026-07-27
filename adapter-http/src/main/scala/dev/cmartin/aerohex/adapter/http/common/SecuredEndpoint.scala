package dev.cmartin.aerohex.adapter.http.common

import dev.cmartin.aerohex.adapter.http.error.{ErrorMapper, HttpErrorResponse}
import dev.cmartin.aerohex.domain.user.{TokenService, ValidatedToken}
import sttp.model.StatusCode
import zio.IO

// Reused by every resource's XxxRoutes (plans/security/protect-endpoints.md decision 2) so the
// TokenService.validate -> ErrorMapper wiring exists in exactly one place instead of once per
// resource. Returns the full ValidatedToken (not just the username) since step 3's logout
// endpoint needs jti/expiresAt too (plans/security/logout.md decision 2) — every other
// resource's Routes class already discards this value (`.serverLogic { _ => args => ... }`),
// so widening it here needed no changes there.
object SecuredEndpoint:
  def securityLogic(tokenService: TokenService)(token: String): IO[(StatusCode, HttpErrorResponse), ValidatedToken] =
    tokenService.validate(token).mapError(ErrorMapper.toHttpError)
