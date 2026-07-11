package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait CountryRepository {
  // Checks membership against the standalone `country_codes` ISO 3166-1 alpha-2 reference
  // table — unrelated to `countries` (no FK), used only to validate a code before creation.
  def isValidCode(code: CountryCode): IO[DomainError, Boolean]
  def findByCode(code: CountryCode): IO[DomainError, Option[Country]]
  def findAll(pagination: Pagination): UIO[List[Country]]
  def searchByName(query: String): UIO[List[Country]]
  def save(country: Country): IO[DomainError, Country]
  def update(country: Country): IO[DomainError, Country]
  def delete(code: CountryCode): IO[DomainError, Unit]
}
