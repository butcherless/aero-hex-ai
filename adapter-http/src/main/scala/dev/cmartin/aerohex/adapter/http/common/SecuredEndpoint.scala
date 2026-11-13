package dev.cmartin.aerohex.adapter.http.common

import dev.cmartin.aerohex.adapter.http.error.{ErrorMapper, HttpErrorResponse}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.user.{TokenService, ValidatedToken}
import sttp.model.StatusCode
import sttp.tapir.Endpoint
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.IO

// Reused by every resource's XxxRoutes (plans/security/protect-endpoints.md decision 2) so the
// TokenService.validate -> ErrorMapper wiring exists in exactly one place instead of once per
// resource. Returns the full ValidatedToken (not just the username) since the logout endpoint
// needs jti/expiresAt too (plans/security/logout.md decision 2).
object SecuredEndpoint:
  type ErrorOut = (StatusCode, HttpErrorResponse)

  def securityLogic(tokenService: TokenService)(token: String): IO[ErrorOut, ValidatedToken] =
    tokenService.validate(token).mapError(ErrorMapper.toHttpError)

  /** Builds server endpoints that check the bearer token first, then run
    * business logic whose `DomainError`s are mapped to HTTP errors. Each Routes
    * class creates one and uses it for every endpoint it serves:
    * {{{
    * private val secured = SecuredEndpoint(tokenService)
    * secured(CountryEndpoints.findByCode)(code => findSvc.findByCode(...).map(CountryDto.fromDomain))
    * }}}
    * The logic ignores the token. An endpoint that needs it (logout) uses
    * [[securityLogic]] directly.
    */
  final class Secured(tokenService: TokenService):
    def apply[I, O](endpoint: Endpoint[String, I, ErrorOut, O, Any])(
        logic: I => IO[DomainError, O]
    ): ZServerEndpoint[Any, Any] =
      endpoint
        .zServerSecurityLogic(securityLogic(tokenService))
        .serverLogic(_ => input => logic(input).mapError(ErrorMapper.toHttpError))

  def apply(tokenService: TokenService): Secured = new Secured(tokenService)
