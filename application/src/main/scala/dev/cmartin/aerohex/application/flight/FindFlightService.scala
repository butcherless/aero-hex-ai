package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.FlightNotFound
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode}
import dev.cmartin.aerohex.domain.flight.FindFlightUseCase
import dev.cmartin.aerohex.domain.flight.FlightRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

final class FindFlightService(repo: FlightRepository) extends FindFlightUseCase {

  override def findByCode(code: String): IO[DomainError, Flight] =
    repo.findByCode(FlightCode(code)).flatMap {
      case Some(f) => ZIO.succeed(f)
      case None    => ZIO.fail(FlightNotFound(code))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Flight]] =
    repo.findAll(pagination)
}

object FindFlightService {
  val layer: URLayer[FlightRepository, FindFlightUseCase] =
    ZLayer.fromFunction(new FindFlightService(_))
}
