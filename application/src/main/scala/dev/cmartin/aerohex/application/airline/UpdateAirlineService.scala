package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.{UpdateAirlineCommand, UpdateAirlineUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class UpdateAirlineService(repo: AirlineRepository) extends UpdateAirlineUseCase:

  override def update(command: UpdateAirlineCommand): IO[DomainError, Airline] =
    repo.update(Airline(command.icao, command.name, command.foundationDate), command.countryCode) @@
      ServiceAspect.logged(s"UpdateAirlineService.update(${command.icao.value})")

object UpdateAirlineService:
  val layer: URLayer[AirlineRepository, UpdateAirlineUseCase] =
    ZLayer.fromFunction(new UpdateAirlineService(_))
