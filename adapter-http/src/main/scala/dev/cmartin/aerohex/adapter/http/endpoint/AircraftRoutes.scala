package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.AircraftDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.FindAircraftUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AircraftRoutes(useCase: FindAircraftUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AircraftEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AircraftDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AircraftEndpoints.findByRegistration.zServerLogic { registration =>
      useCase
        .findByRegistration(registration)
        .map(AircraftDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AircraftRoutes:
  val layer: URLayer[FindAircraftUseCase, AircraftRoutes] =
    ZLayer.fromFunction(new AircraftRoutes(_))
