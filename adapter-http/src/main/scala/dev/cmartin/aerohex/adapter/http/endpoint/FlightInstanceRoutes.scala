package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.FlightInstanceDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.FindFlightInstanceUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class FlightInstanceRoutes(useCase: FindFlightInstanceUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    FlightInstanceEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(FlightInstanceDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    FlightInstanceEndpoints.findById.zServerLogic { id =>
      useCase
        .findById(id)
        .map(FlightInstanceDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object FlightInstanceRoutes:
  val layer: URLayer[FindFlightInstanceUseCase, FlightInstanceRoutes] =
    ZLayer.fromFunction(new FlightInstanceRoutes(_))
