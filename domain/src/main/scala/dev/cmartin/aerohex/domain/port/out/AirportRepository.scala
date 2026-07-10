package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait AirportRepository {
  def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airport]]
  def searchByName(query: String): IO[DomainError, List[Airport]]
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]]
  def save(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def update(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport]
  def delete(iata: IataCode): IO[DomainError, Unit]
}
