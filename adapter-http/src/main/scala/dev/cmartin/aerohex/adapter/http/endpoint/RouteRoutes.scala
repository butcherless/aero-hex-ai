package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.RouteDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.{CreateRouteCommand, CreateRouteUseCase}
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class RouteRoutes(useCase: CreateRouteUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    RouteEndpoints.create.zServerLogic { req =>
      useCase
        .create(CreateRouteCommand(req.originIata, req.destinationIata, req.airlineIcao, req.distanceKm))
        .map(RouteDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object RouteRoutes:
  val layer: URLayer[CreateRouteUseCase, RouteRoutes] =
    ZLayer.fromFunction(new RouteRoutes(_))
