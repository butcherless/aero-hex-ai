package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.DeleteAirlineUseCase
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class DeleteAirlineService(repo: AirlineRepository) extends DeleteAirlineUseCase:

  override def delete(icao: AirlineIcaoCode): IO[DomainError, Unit] =
    repo.delete(icao) @@ ServiceAspect.logged(s"DeleteAirlineService.delete(${icao.value})")

object DeleteAirlineService:
  val layer: URLayer[AirlineRepository, DeleteAirlineUseCase] =
    ZLayer.fromFunction(new DeleteAirlineService(_))
