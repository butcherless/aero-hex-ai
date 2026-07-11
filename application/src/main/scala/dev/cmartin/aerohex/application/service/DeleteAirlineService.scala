package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.IcaoCode
import dev.cmartin.aerohex.domain.port.in.DeleteAirlineUseCase
import dev.cmartin.aerohex.domain.port.out.AirlineRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteAirlineService(repo: AirlineRepository) extends DeleteAirlineUseCase:

  override def delete(icao: IcaoCode): IO[DomainError, Unit] =
    repo.delete(icao) @@ ServiceAspect.logged(s"DeleteAirlineService.delete(${icao.value})")

object DeleteAirlineService:
  val layer: URLayer[AirlineRepository, DeleteAirlineUseCase] =
    ZLayer.fromFunction(new DeleteAirlineService(_))
