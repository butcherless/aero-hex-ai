package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Country
import dev.cmartin.aerohex.domain.port.in.{CreateCountryCommand, CreateCountryUseCase}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateCountryService(repo: CountryRepository) extends CreateCountryUseCase:

  override def create(command: CreateCountryCommand): IO[DomainError, Country] =
    val effect = repo.findByCode(command.code).flatMap:
      case Some(_) => ZIO.fail(DomainError.CountryAlreadyExists(command.code.value))
      case None    => repo.save(Country(command.code, command.name))
    effect @@ ServiceAspect.logged(s"CreateCountryService.create(${command.code.value})")

object CreateCountryService:
  val layer: URLayer[CountryRepository, CreateCountryUseCase] =
    ZLayer.fromFunction(new CreateCountryService(_))
