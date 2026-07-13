package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.DeleteFlightUseCase
import dev.cmartin.aerohex.domain.flight.FlightCode
import dev.cmartin.aerohex.domain.flight.FlightRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteFlightService(repo: FlightRepository) extends DeleteFlightUseCase:

  override def delete(code: FlightCode): IO[DomainError, Unit] =
    repo.delete(code) @@ ServiceAspect.logged(s"DeleteFlightService.delete(${code.value})")

object DeleteFlightService:
  val layer: URLayer[FlightRepository, DeleteFlightUseCase] =
    ZLayer.fromFunction(new DeleteFlightService(_))
