package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.common.CountryScopedFinder
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.FindAirlinesByCountryUseCase
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAirlinesByCountryService(
    protected val countryRepository: CountryRepository,
    airlineRepository: AirlineRepository
) extends FindAirlinesByCountryUseCase with CountryScopedFinder {

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]] =
    findByCountryChecked(code, pagination, "FindAirlinesByCountryService")(airlineRepository.findByCountry)
}

object FindAirlinesByCountryService {
  val layer: URLayer[CountryRepository & AirlineRepository, FindAirlinesByCountryUseCase] =
    ZLayer.fromFunction(new FindAirlinesByCountryService(_, _))
}
