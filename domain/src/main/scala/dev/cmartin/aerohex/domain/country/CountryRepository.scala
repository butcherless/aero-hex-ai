package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait CountryRepository {
  // Checks membership against the standalone `country_codes` ISO 3166-1 alpha-2 reference
  // table — unrelated to `countries` (no FK). Fails with InvalidCountryCode if not a member;
  // succeeds with unit otherwise — the caller has nothing to do with a boolean either way.
  def validateCode(code: CountryCode): IO[DomainError, Unit]
  def findByCode(code: CountryCode): IO[DomainError, Option[Country]]
  def findAll(pagination: Pagination): UIO[List[Country]]
  def searchByName(query: String): UIO[List[Country]]
  def save(country: Country): IO[DomainError, Country]
  def update(country: Country): IO[DomainError, Country]
  def delete(code: CountryCode): IO[DomainError, Unit]
}
