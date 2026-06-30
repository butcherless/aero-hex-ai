package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Country
import dev.cmartin.aerohex.domain.port.in.{UpdateCountryCommand, UpdateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZLayer}

final class UpdateCountryService(repo: CountryRepository) extends UpdateCountryUseCase:

  override def update(command: UpdateCountryCommand): IO[DomainError, Country] =
    repo.update(Country(command.code, command.name)) @@
      ServiceAspect.logged(s"UpdateCountryService.update(${command.code.value})")

object UpdateCountryService:
  val layer: URLayer[CountryRepository, UpdateCountryUseCase] =
    ZLayer.fromFunction(new UpdateCountryService(_))
