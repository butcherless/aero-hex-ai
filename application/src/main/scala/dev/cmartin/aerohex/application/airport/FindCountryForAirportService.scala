package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.{AirportRepository, FindCountryForAirportUseCase, IataCode}
import dev.cmartin.aerohex.domain.country.Country
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AirportNotFound
import zio.{IO, URLayer, ZLayer}

final class FindCountryForAirportService(airportRepository: AirportRepository) extends FindCountryForAirportUseCase {

  override def findCountry(iata: IataCode): IO[DomainError, Country] =
    airportRepository.findCountryByIata(iata).someOrFail(AirportNotFound(iata.value)) @@
      ServiceAspect.logged(s"FindCountryForAirportService.findCountry(${iata.value})")
}

object FindCountryForAirportService {
  val layer: URLayer[AirportRepository, FindCountryForAirportUseCase] =
    ZLayer.fromFunction(new FindCountryForAirportService(_))
}
