package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.CountryCode
import dev.cmartin.aerohex.domain.port.in.DeleteCountryUseCase
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteCountryService(repo: CountryRepository) extends DeleteCountryUseCase:

  override def delete(code: CountryCode): IO[DomainError, Unit] =
    repo.delete(code) @@ ServiceAspect.logged(s"DeleteCountryService.delete(${code.value})")

object DeleteCountryService:
  val layer: URLayer[CountryRepository, DeleteCountryUseCase] =
    ZLayer.fromFunction(new DeleteCountryService(_))
