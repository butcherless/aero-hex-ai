package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{CreateCountryCommand, CreateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateCountryService(repo: CountryRepository) extends CreateCountryUseCase:

  override def create(command: CreateCountryCommand): IO[DomainError, Country] =
    ZIO.logDebug(s"create - command: $command") *>
      repo.findByCode(CountryCode(command.code)).flatMap:
        case Some(_) =>
          ZIO.logDebug(s"create - country already exists: ${command.code}") *>
            ZIO.fail(DomainError.CountryAlreadyExists(command.code))
        case None    =>
          repo.save(Country(CountryCode(command.code), command.name))
            .tap(c => ZIO.logDebug(s"create - country saved: ${c.code.value}"))

object CreateCountryService:
  val layer: URLayer[CountryRepository, CreateCountryUseCase] =
    ZLayer.fromFunction(new CreateCountryService(_))
