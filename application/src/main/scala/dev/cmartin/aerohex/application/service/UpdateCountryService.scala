package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Country
import dev.cmartin.aerohex.domain.port.in.{UpdateCountryCommand, UpdateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class UpdateCountryService(repo: CountryRepository) extends UpdateCountryUseCase:

  override def update(command: UpdateCountryCommand): IO[DomainError, Country] =
    val effect = repo.findByCode(command.code).flatMap:
      case None    => ZIO.fail(DomainError.CountryNotFound(command.code.value))
      case Some(_) => repo.save(Country(command.code, command.name))
    effect @@ ServiceAspect.logged(s"UpdateCountryService.update(${command.code.value})")

object UpdateCountryService:
  val layer: URLayer[CountryRepository, UpdateCountryUseCase] =
    ZLayer.fromFunction(new UpdateCountryService(_))
