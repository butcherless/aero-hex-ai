package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Airport
import dev.cmartin.aerohex.domain.port.in.{CreateAirportCommand, CreateAirportUseCase}
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateAirportService(repo: AirportRepository) extends CreateAirportUseCase:

  override def create(command: CreateAirportCommand): IO[DomainError, Airport] =
    val effect = repo.findByIata(command.iataCode).flatMap:
      case Some(_) => ZIO.fail(DomainError.AirportAlreadyExists(command.iataCode.value))
      case None    =>
        repo.save(Airport(command.iataCode, command.icaoCode, command.name, command.city), command.countryCode)
    effect @@ ServiceAspect.logged(s"CreateAirportService.create(${command.iataCode.value})")

object CreateAirportService:
  val layer: URLayer[AirportRepository, CreateAirportUseCase] =
    ZLayer.fromFunction(new CreateAirportService(_))
