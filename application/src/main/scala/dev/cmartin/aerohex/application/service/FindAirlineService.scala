package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AirlineNotFound
import dev.cmartin.aerohex.domain.model.{Airline, IcaoCode}
import dev.cmartin.aerohex.domain.port.in.FindAirlineUseCase
import dev.cmartin.aerohex.domain.port.out.AirlineRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

final class FindAirlineService(repo: AirlineRepository) extends FindAirlineUseCase {

  override def findByIcao(icao: String): IO[DomainError, Airline] =
    repo.findByIcao(IcaoCode(icao)).flatMap {
      case Some(airline) => ZIO.succeed(airline)
      case None          => ZIO.fail(AirlineNotFound(icao))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Airline]] =
    repo.findAll(pagination)
}

object FindAirlineService {
  val layer: URLayer[AirlineRepository, FindAirlineUseCase] =
    ZLayer.fromFunction(new FindAirlineService(_))
}
