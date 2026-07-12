package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.Airport
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.airport.{UpdateAirportCommand, UpdateAirportUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class UpdateAirportService(repo: AirportRepository) extends UpdateAirportUseCase:

  override def update(command: UpdateAirportCommand): IO[DomainError, Airport] =
    repo.update(Airport(command.iataCode, command.icaoCode, command.name, command.city), command.countryCode) @@
      ServiceAspect.logged(s"UpdateAirportService.update(${command.iataCode.value})")

object UpdateAirportService:
  val layer: URLayer[AirportRepository, UpdateAirportUseCase] =
    ZLayer.fromFunction(new UpdateAirportService(_))
