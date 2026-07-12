package dev.cmartin.aerohex.application.country

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.DeleteCountryUseCase
import dev.cmartin.aerohex.domain.country.CountryRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteCountryService(repo: CountryRepository) extends DeleteCountryUseCase:

  override def delete(code: CountryCode): IO[DomainError, Unit] =
    repo.delete(code) @@ ServiceAspect.logged(s"DeleteCountryService.delete(${code.value})")

object DeleteCountryService:
  val layer: URLayer[CountryRepository, DeleteCountryUseCase] =
    ZLayer.fromFunction(new DeleteCountryService(_))
