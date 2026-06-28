package domain.port.out

import domain.error.DomainError
import domain.model.{Country, CountryCode}
import shared.Pagination
import zio.IO

trait CountryRepository {
  def findByCode(code: CountryCode): IO[DomainError, Option[Country]]
  def findAll(pagination: Pagination): IO[DomainError, List[Country]]
  def searchByName(query: String): IO[DomainError, List[Country]]
  def save(country: Country): IO[DomainError, Country]
  def delete(code: CountryCode): IO[DomainError, Unit]
}
