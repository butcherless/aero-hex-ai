package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.AirportDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.FindAirportUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirportRoutes(useCase: FindAirportUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AirportEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirportDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirportEndpoints.findByIata.zServerLogic { iata =>
      useCase
        .findByIata(iata)
        .map(AirportDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirportRoutes:
  val layer: URLayer[FindAirportUseCase, AirportRoutes] =
    ZLayer.fromFunction(new AirportRoutes(_))
