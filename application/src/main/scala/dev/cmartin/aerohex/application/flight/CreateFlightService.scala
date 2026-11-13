package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.Flight
import dev.cmartin.aerohex.domain.flight.FlightRepository
import dev.cmartin.aerohex.domain.flight.{CreateFlightCommand, CreateFlightUseCase}
import zio.{IO, URLayer, ZLayer}

final class CreateFlightService(repo: FlightRepository) extends CreateFlightUseCase:

  override def create(command: CreateFlightCommand): IO[DomainError, Flight] =
    // No lookup before the insert: the repository already reports a duplicate natural key as
    // FlightAlreadyExists (via the table's UNIQUE constraint), which also covers a concurrent insert.
    val effect = repo.save(
      Flight(
        command.code,
        command.alias,
        command.schedDeparture,
        command.schedArrival,
        command.origin,
        command.destination,
        command.airlineIcao
      )
    )
    effect @@ ServiceAspect.logged(s"CreateFlightService.create(${command.code.value})")

object CreateFlightService:
  val layer: URLayer[FlightRepository, CreateFlightUseCase] =
    ZLayer.fromFunction(new CreateFlightService(_))
