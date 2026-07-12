package dev.cmartin.aerohex.application.country

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.country.Country
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.country.{UpdateCountryCommand, UpdateCountryUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class UpdateCountryService(repo: CountryRepository) extends UpdateCountryUseCase:

  override def update(command: UpdateCountryCommand): IO[DomainError, Country] =
    repo.update(Country(command.code, command.name)) @@
      ServiceAspect.logged(s"UpdateCountryService.update(${command.code.value})")

object UpdateCountryService:
  val layer: URLayer[CountryRepository, UpdateCountryUseCase] =
    ZLayer.fromFunction(new UpdateCountryService(_))
