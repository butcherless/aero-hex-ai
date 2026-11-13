package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindAirportUseCase {
  def findByIata(iata: String): IO[DomainError, Airport]
  def findAll(pagination: Pagination): UIO[List[Airport]]
  def findAllUnbounded: UIO[List[Airport]]
  def findAllUnboundedWithCountry: UIO[List[(Airport, CountryCode)]]
  def searchByName(query: String): UIO[List[Airport]]
}
