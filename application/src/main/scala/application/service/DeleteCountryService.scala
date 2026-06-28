package application.service

import domain.error.DomainError
import domain.model.CountryCode
import domain.port.in.DeleteCountryUseCase
import domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class DeleteCountryService(repo: CountryRepository) extends DeleteCountryUseCase:

  override def delete(code: String): IO[DomainError, Unit] =
    repo.findByCode(CountryCode(code)).flatMap:
      case None    => ZIO.fail(DomainError.CountryNotFound(code))
      case Some(_) => repo.delete(CountryCode(code))

object DeleteCountryService:
  val layer: URLayer[CountryRepository, DeleteCountryUseCase] =
    ZLayer.fromFunction(new DeleteCountryService(_))
