package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{CreateCountryCommand, CreateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateCountryService(repo: CountryRepository) extends CreateCountryUseCase:

  override def create(command: CreateCountryCommand): IO[DomainError, Country] =
    repo.findByCode(CountryCode(command.code)).flatMap:
      case Some(_) => ZIO.fail(DomainError.CountryAlreadyExists(command.code))
      case None    => repo.save(Country(CountryCode(command.code), command.name))

object CreateCountryService:
  val layer: URLayer[CountryRepository, CreateCountryUseCase] =
    ZLayer.fromFunction(new CreateCountryService(_))
