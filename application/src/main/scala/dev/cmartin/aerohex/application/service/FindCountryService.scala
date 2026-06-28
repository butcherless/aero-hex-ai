package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.CountryNotFound
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.FindCountryUseCase
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

final class FindCountryService(repo: CountryRepository) extends FindCountryUseCase {

  override def findByCode(code: String): IO[DomainError, Country] =
    repo.findByCode(CountryCode(code)).flatMap {
      case Some(country) => ZIO.succeed(country)
      case None          => ZIO.fail(CountryNotFound(code))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Country]] =
    repo.findAll(pagination)

  override def searchByName(query: String): IO[DomainError, List[Country]] =
    repo.searchByName(query)
}

object FindCountryService {
  val layer: URLayer[CountryRepository, FindCountryUseCase] =
    ZLayer.fromFunction(new FindCountryService(_))
}
