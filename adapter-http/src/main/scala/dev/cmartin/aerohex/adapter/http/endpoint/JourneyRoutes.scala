package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.JourneyDto
import dev.cmartin.aerohex.adapter.http.error.ErrorMapper
import dev.cmartin.aerohex.domain.port.in.FindJourneyUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import zio.*

class JourneyRoutes(useCase: FindJourneyUseCase):
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    JourneyEndpoints.findAll.zServerLogic { (page, pageSize) =>
      useCase
        .findAll(Pagination(page, pageSize))
        .map(_.map(JourneyDto.fromDomain))
        .mapError(ErrorMapper.toHttpError)
    },
    JourneyEndpoints.findById.zServerLogic { id =>
      useCase
        .findById(id)
        .map(JourneyDto.fromDomain)
        .mapError(ErrorMapper.toHttpError)
    }
  )

object JourneyRoutes:
  val layer: URLayer[FindJourneyUseCase, JourneyRoutes] =
    ZLayer.fromFunction(new JourneyRoutes(_))
