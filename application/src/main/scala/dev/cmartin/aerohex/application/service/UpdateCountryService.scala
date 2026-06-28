package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{UpdateCountryCommand, UpdateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class UpdateCountryService(repo: CountryRepository) extends UpdateCountryUseCase:

  override def update(command: UpdateCountryCommand): IO[DomainError, Country] =
    repo.findByCode(CountryCode(command.code)).flatMap:
      case None    => ZIO.fail(DomainError.CountryNotFound(command.code))
      case Some(_) => repo.save(Country(CountryCode(command.code), command.name))

object UpdateCountryService:
  val layer: URLayer[CountryRepository, UpdateCountryUseCase] =
    ZLayer.fromFunction(new UpdateCountryService(_))
