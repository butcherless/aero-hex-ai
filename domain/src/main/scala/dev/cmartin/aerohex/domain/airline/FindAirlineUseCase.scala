package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindAirlineUseCase {
  def findByIcao(icao: String): IO[DomainError, Airline]
  def findAll(pagination: Pagination): UIO[List[Airline]]
  def findAllUnbounded: UIO[List[Airline]]
  def findAllUnboundedWithCountry: UIO[List[(Airline, CountryCode)]]
  def searchByName(query: String): UIO[List[Airline]]
}
