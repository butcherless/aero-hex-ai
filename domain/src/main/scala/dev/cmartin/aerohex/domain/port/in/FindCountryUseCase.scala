package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindCountryUseCase {
  def findByCode(code: CountryCode): IO[DomainError, Country]
  def findAll(pagination: Pagination): IO[DomainError, List[Country]]
  def searchByName(query: String): IO[DomainError, List[Country]]
}
