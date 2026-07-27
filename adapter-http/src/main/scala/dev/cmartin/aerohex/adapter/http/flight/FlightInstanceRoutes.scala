package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.flight.FindFlightInstanceUseCase
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class FlightInstanceRoutes(useCase: FindFlightInstanceUseCase, tokenService: TokenService):
  private val secured = SecuredEndpoint.securityLogic(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    FlightInstanceEndpoints.findAll.zServerSecurityLogic(secured).serverLogic { _ => (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightInstanceDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    FlightInstanceEndpoints.findById.zServerSecurityLogic(secured).serverLogic { _ => id =>
      useCase
        .findById(id)
        .map(FlightInstanceDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object FlightInstanceRoutes:
  val layer: URLayer[FindFlightInstanceUseCase & TokenService, FlightInstanceRoutes] =
    ZLayer.fromFunction(new FlightInstanceRoutes(_, _))
