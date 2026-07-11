package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.IataCode
import dev.cmartin.aerohex.domain.port.in.DeleteAirportUseCase
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteAirportService(repo: AirportRepository) extends DeleteAirportUseCase:

  override def delete(iata: IataCode): IO[DomainError, Unit] =
    repo.delete(iata) @@ ServiceAspect.logged(s"DeleteAirportService.delete(${iata.value})")

object DeleteAirportService:
  val layer: URLayer[AirportRepository, DeleteAirportUseCase] =
    ZLayer.fromFunction(new DeleteAirportService(_))
