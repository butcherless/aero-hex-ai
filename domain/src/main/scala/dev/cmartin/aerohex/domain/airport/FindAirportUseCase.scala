package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirportUseCase {
  def findByIata(iata: String): IO[DomainError, Airport]
  def findAll(pagination: Pagination): IO[DomainError, List[Airport]]
  def findAllUnbounded: IO[DomainError, List[Airport]]
  def findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]]
  def searchByName(query: String): IO[DomainError, List[Airport]]
}
