package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode}
import dev.cmartin.aerohex.domain.port.in.FindAirportsByCountryUseCase
import dev.cmartin.aerohex.domain.port.out.{AirportRepository, CountryRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZIO, ZLayer}

final class FindAirportsByCountryService(countryRepository: CountryRepository, airportRepository: AirportRepository)
    extends FindAirportsByCountryUseCase {

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] =
    countryRepository.findByCode(code).flatMap {
      case None    => ZIO.fail(DomainError.CountryNotFound(code.value))
      case Some(_) => airportRepository.findByCountry(code, pagination)
    }
}

object FindAirportsByCountryService {
  val layer: URLayer[CountryRepository & AirportRepository, FindAirportsByCountryUseCase] =
    ZLayer.fromFunction(new FindAirportsByCountryService(_, _))
}
