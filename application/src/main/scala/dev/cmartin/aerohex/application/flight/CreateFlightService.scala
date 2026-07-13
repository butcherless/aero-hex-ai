package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.Flight
import dev.cmartin.aerohex.domain.flight.FlightRepository
import dev.cmartin.aerohex.domain.flight.{CreateFlightCommand, CreateFlightUseCase}
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateFlightService(repo: FlightRepository) extends CreateFlightUseCase:

  override def create(command: CreateFlightCommand): IO[DomainError, Flight] =
    val effect = repo.findByCode(command.code).flatMap:
      case Some(_) => ZIO.fail(DomainError.FlightAlreadyExists(command.code.value))
      case None    =>
        repo.save(
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
