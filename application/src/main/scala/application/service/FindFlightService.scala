package application.service

import domain.error.DomainError
import domain.error.DomainError.FlightNotFound
import domain.model.{Flight, FlightCode}
import domain.port.in.FindFlightUseCase
import domain.port.out.FlightRepository
import shared.Pagination
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
