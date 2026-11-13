package dev.cmartin.aerohex.application.country

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.country.Country
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.domain.country.{CreateCountryCommand, CreateCountryUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateCountryService(repo: CountryRepository) extends CreateCountryUseCase:

  override def create(command: CreateCountryCommand): IO[DomainError, Country] =
    // No lookup before the insert: the repository already reports a duplicate natural key as
    // CountryAlreadyExists (via the table's UNIQUE constraint), which also covers a concurrent insert.
    val effect =
      ZIO.fromEither(CountryCode.validateIso(command.code)) *>
        repo.save(Country(command.code, command.name))
    effect @@ ServiceAspect.logged(s"CreateCountryService.create(${command.code.value})")

object CreateCountryService:
  val layer: URLayer[CountryRepository, CreateCountryUseCase] =
    ZLayer.fromFunction(new CreateCountryService(_))
