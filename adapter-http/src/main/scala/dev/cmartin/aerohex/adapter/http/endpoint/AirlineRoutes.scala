package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.AirlineDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.FindAirlineUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class AirlineRoutes(useCase: FindAirlineUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    AirlineEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(AirlineDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    AirlineEndpoints.findByIcao.zServerLogic { icao =>
      useCase
        .findByIcao(icao)
        .map(AirlineDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object AirlineRoutes:
  val layer: URLayer[FindAirlineUseCase, AirlineRoutes] =
    ZLayer.fromFunction(new AirlineRoutes(_))
