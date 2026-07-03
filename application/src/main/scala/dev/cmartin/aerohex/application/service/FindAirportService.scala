package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AirportNotFound
import dev.cmartin.aerohex.domain.model.{Airport, IataCode}
import dev.cmartin.aerohex.domain.port.in.FindAirportUseCase
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

final class FindAirportService(repo: AirportRepository) extends FindAirportUseCase {

  override def findByIata(iata: String): IO[DomainError, Airport] =
    repo.findByIata(IataCode(iata)).flatMap {
      case Some(airport) => ZIO.succeed(airport)
      case None          => ZIO.fail(AirportNotFound(iata))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] =
    repo.findAll(pagination)

  override def searchByName(query: String): IO[DomainError, List[Airport]] =
    repo.searchByName(query)
}

object FindAirportService {
  val layer: URLayer[AirportRepository, FindAirportUseCase] =
    ZLayer.fromFunction(new FindAirportService(_))
}
