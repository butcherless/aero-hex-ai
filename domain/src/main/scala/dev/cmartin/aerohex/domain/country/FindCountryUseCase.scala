package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindCountryUseCase {
  def findByCode(code: CountryCode): IO[DomainError, Country]
  def findAll(pagination: Pagination): UIO[List[Country]]
  def searchByName(query: String): UIO[List[Country]]
}
