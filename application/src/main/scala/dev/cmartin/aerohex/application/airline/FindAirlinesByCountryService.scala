package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.FindAirlinesByCountryUseCase
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZIO, ZLayer}

final class FindAirlinesByCountryService(countryRepository: CountryRepository, airlineRepository: AirlineRepository)
    extends FindAirlinesByCountryUseCase {

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]] =
    countryRepository.findByCode(code).flatMap {
      case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
      case Some(_) => airlineRepository.findByCountry(code, pagination)
    } @@ ServiceAspect.logged(s"FindAirlinesByCountryService.findByCountry(${code.value})")
}

object FindAirlinesByCountryService {
  val layer: URLayer[CountryRepository & AirlineRepository, FindAirlinesByCountryUseCase] =
    ZLayer.fromFunction(new FindAirlinesByCountryService(_, _))
}
