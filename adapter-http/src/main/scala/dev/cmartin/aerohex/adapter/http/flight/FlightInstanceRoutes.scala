package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.common.SecuredEndpoint
import dev.cmartin.aerohex.domain.flight.FindFlightInstanceUseCase
import dev.cmartin.aerohex.domain.user.TokenService
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

class FlightInstanceRoutes(useCase: FindFlightInstanceUseCase, tokenService: TokenService):
  private val secured = SecuredEndpoint(tokenService)

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    secured(FlightInstanceEndpoints.findAll) { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightInstanceDto.fromDomain))
    },
    secured(FlightInstanceEndpoints.findById) { id =>
      useCase
        .findById(id)
        .map(FlightInstanceDto.fromDomain)
    }
  )

object FlightInstanceRoutes:
  val layer: URLayer[FindFlightInstanceUseCase & TokenService, FlightInstanceRoutes] =
    ZLayer.fromFunction(new FlightInstanceRoutes(_, _))
