package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait AirlineRepository {
  def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airline]]
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]]
  def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline]
  def update(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline]
  def delete(icao: IcaoCode): IO[DomainError, Unit]
}
