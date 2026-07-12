package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.flight.FindFlightUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class FlightRoutes(useCase: FindFlightUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    FlightEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    FlightEndpoints.findByCode.zServerLogic { code =>
      useCase
        .findByCode(code)
        .map(FlightDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object FlightRoutes:
  val layer: URLayer[FindFlightUseCase, FlightRoutes] =
    ZLayer.fromFunction(new FlightRoutes(_))
