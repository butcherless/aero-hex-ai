package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.common.CountryScopedFinder
import dev.cmartin.aerohex.domain.airport.Airport
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.airport.FindAirportsByCountryUseCase
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAirportsByCountryService(
    protected val countryRepository: CountryRepository,
    airportRepository: AirportRepository
) extends FindAirportsByCountryUseCase with CountryScopedFinder {

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] =
    findByCountryChecked(code, pagination, "FindAirportsByCountryService")(airportRepository.findByCountry)
}

object FindAirportsByCountryService {
  val layer: URLayer[CountryRepository & AirportRepository, FindAirportsByCountryUseCase] =
    ZLayer.fromFunction(new FindAirportsByCountryService(_, _))
}
