package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait AirlineRepository {
  def findByIcao(icao: AirlineIcaoCode): IO[DomainError, Option[Airline]]
  def findAll(pagination: Pagination): UIO[List[Airline]]
  def findAllUnbounded: UIO[List[Airline]]
  def findAllUnboundedWithCountry: UIO[List[(Airline, CountryCode)]]
  def searchByName(query: String): UIO[List[Airline]]
  def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]]
  def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline]
  def update(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline]
  def delete(icao: AirlineIcaoCode): IO[DomainError, Unit]
}
