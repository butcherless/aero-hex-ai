package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.Flight
import dev.cmartin.aerohex.domain.flight.FlightRepository
import dev.cmartin.aerohex.domain.flight.{UpdateFlightCommand, UpdateFlightUseCase}
import zio.{IO, URLayer, ZLayer}

final class UpdateFlightService(repo: FlightRepository) extends UpdateFlightUseCase:

  override def update(command: UpdateFlightCommand): IO[DomainError, Flight] =
    repo.update(
      Flight(
        command.code,
        command.alias,
        command.schedDeparture,
        command.schedArrival,
        command.origin,
        command.destination,
        command.airlineIcao
      )
    ) @@ ServiceAspect.logged(s"UpdateFlightService.update(${command.code.value})")

object UpdateFlightService:
  val layer: URLayer[FlightRepository, UpdateFlightUseCase] =
    ZLayer.fromFunction(new UpdateFlightService(_))
