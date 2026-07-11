package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Airline
import dev.cmartin.aerohex.domain.port.in.{UpdateAirlineCommand, UpdateAirlineUseCase}
import dev.cmartin.aerohex.domain.port.out.AirlineRepository
import zio.{IO, URLayer, ZLayer}

final class UpdateAirlineService(repo: AirlineRepository) extends UpdateAirlineUseCase:

  override def update(command: UpdateAirlineCommand): IO[DomainError, Airline] =
    repo.update(Airline(command.icao, command.name, command.foundationDate), command.countryCode) @@
      ServiceAspect.logged(s"UpdateAirlineService.update(${command.icao.value})")

object UpdateAirlineService:
  val layer: URLayer[AirlineRepository, UpdateAirlineUseCase] =
    ZLayer.fromFunction(new UpdateAirlineService(_))
