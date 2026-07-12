package dev.cmartin.aerohex.application.country

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.country.FindCountryUseCase
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.CountryNotFound
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO, URLayer, ZLayer}

final class FindCountryService(repo: CountryRepository) extends FindCountryUseCase:

  override def findByCode(code: CountryCode): IO[DomainError, Country] =
    repo.findByCode(code).someOrFail(CountryNotFound(code.value)) @@
      ServiceAspect.logged(s"FindCountryService.findByCode(${code.value})")

  override def findAll(pagination: Pagination): UIO[List[Country]] =
    ServiceAspect.logged("FindCountryService.findAll").apply(repo.findAll(pagination))

  override def searchByName(query: String): UIO[List[Country]] =
    ServiceAspect.logged(s"FindCountryService.searchByName($query)").apply(repo.searchByName(query))

object FindCountryService:
  val layer: URLayer[CountryRepository, FindCountryUseCase] =
    ZLayer.fromFunction(new FindCountryService(_))
