package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.CountryCode
import dev.cmartin.aerohex.domain.port.in.DeleteCountryUseCase
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class DeleteCountryService(repo: CountryRepository) extends DeleteCountryUseCase:

  override def delete(code: String): IO[DomainError, Unit] =
    repo.findByCode(CountryCode(code)).flatMap:
      case None    => ZIO.fail(DomainError.CountryNotFound(code))
      case Some(_) => repo.delete(CountryCode(code))

object DeleteCountryService:
  val layer: URLayer[CountryRepository, DeleteCountryUseCase] =
    ZLayer.fromFunction(new DeleteCountryService(_))
