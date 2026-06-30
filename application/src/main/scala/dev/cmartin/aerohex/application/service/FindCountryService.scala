package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.CountryNotFound
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.FindCountryUseCase
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindCountryService(repo: CountryRepository) extends FindCountryUseCase:

  override def findByCode(code: CountryCode): IO[DomainError, Country] =
    repo.findByCode(code).someOrFail(CountryNotFound(code.value)) @@
      ServiceAspect.logged(s"FindCountryService.findByCode(${code.value})")

  override def findAll(pagination: Pagination): IO[DomainError, List[Country]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindCountryService.findAll")

  override def searchByName(query: String): IO[DomainError, List[Country]] =
    repo.searchByName(query) @@ ServiceAspect.logged(s"FindCountryService.searchByName($query)")

object FindCountryService:
  val layer: URLayer[CountryRepository, FindCountryUseCase] =
    ZLayer.fromFunction(new FindCountryService(_))
