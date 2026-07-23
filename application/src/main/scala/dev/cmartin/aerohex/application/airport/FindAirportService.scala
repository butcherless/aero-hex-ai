package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.airport.FindAirportUseCase
import dev.cmartin.aerohex.domain.airport.{Airport, IataCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AirportNotFound
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAirportService(repo: AirportRepository) extends FindAirportUseCase {

  override def findByIata(iata: String): IO[DomainError, Airport] =
    repo.findByIata(IataCode.unsafeMake(iata)).someOrFail(AirportNotFound(iata)) @@
      ServiceAspect.logged(s"FindAirportService.findByIata($iata)")

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindAirportService.findAll")

  override def findAllUnbounded: IO[DomainError, List[Airport]] =
    repo.findAllUnbounded @@ ServiceAspect.logged("FindAirportService.findAllUnbounded")

  override def findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]] =
    repo.findAllUnboundedWithCountry @@ ServiceAspect.logged("FindAirportService.findAllUnboundedWithCountry")

  override def searchByName(query: String): IO[DomainError, List[Airport]] =
    repo.searchByName(query) @@ ServiceAspect.logged(s"FindAirportService.searchByName($query)")
}

object FindAirportService {
  val layer: URLayer[AirportRepository, FindAirportUseCase] =
    ZLayer.fromFunction(new FindAirportService(_))
}
