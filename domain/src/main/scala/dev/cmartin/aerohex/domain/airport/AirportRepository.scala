package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait AirportRepository {
  def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]
  def findAll(pagination: Pagination): UIO[List[Airport]]
  def findAllUnbounded: UIO[List[Airport]]
  def findAllUnboundedWithCountry: UIO[List[(Airport, CountryCode)]]
  def searchByName(query: String): UIO[List[Airport]]
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
  def findCountryByIata(iata: IataCode): IO[DomainError, Option[Country]]
  def save(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def update(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def delete(iata: IataCode): IO[DomainError, Unit]
}
