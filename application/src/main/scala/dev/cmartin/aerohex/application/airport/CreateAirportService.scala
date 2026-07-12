package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.Airport
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.airport.{CreateAirportCommand, CreateAirportUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
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
