package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.FlightNotFound
import dev.cmartin.aerohex.domain.flight.FindFlightUseCase
import dev.cmartin.aerohex.domain.flight.FlightRepository
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindFlightService(repo: FlightRepository) extends FindFlightUseCase {

  override def findByCode(code: String): IO[DomainError, Flight] =
    repo.findByCode(FlightCode.unsafeMake(code)).someOrFail(FlightNotFound(code)) @@
      ServiceAspect.logged(s"FindFlightService.findByCode($code)")

  override def findAll(pagination: Pagination): IO[DomainError, List[Flight]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindFlightService.findAll")
}

object FindFlightService {
  val layer: URLayer[FlightRepository, FindFlightUseCase] =
    ZLayer.fromFunction(new FindFlightService(_))
}
