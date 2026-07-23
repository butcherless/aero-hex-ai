package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait AirportRepository {
  def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airport]]
  def findAllUnbounded: IO[DomainError, List[Airport]]
  def findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]]
  def searchByName(query: String): IO[DomainError, List[Airport]]
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
  def findCountryByIata(iata: IataCode): IO[DomainError, Option[Country]]
  def save(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def update(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def delete(iata: IataCode): IO[DomainError, Unit]
}
