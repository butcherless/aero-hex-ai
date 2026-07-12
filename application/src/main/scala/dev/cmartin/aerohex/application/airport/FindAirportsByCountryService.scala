package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airport.Airport
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.airport.FindAirportsByCountryUseCase
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZIO, ZLayer}

final class FindAirportsByCountryService(countryRepository: CountryRepository, airportRepository: AirportRepository)
    extends FindAirportsByCountryUseCase {

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] =
    countryRepository.findByCode(code).flatMap {
      case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
      case Some(_) => airportRepository.findByCountry(code, pagination)
    } @@ ServiceAspect.logged(s"FindAirportsByCountryService.findByCountry(${code.value})")
}

object FindAirportsByCountryService {
  val layer: URLayer[CountryRepository & AirportRepository, FindAirportsByCountryUseCase] =
    ZLayer.fromFunction(new FindAirportsByCountryService(_, _))
}
