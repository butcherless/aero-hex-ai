package dev.cmartin.aerohex.application.airport

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airport.Airport
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.domain.airport.{CreateAirportCommand, CreateAirportUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class CreateAirportService(repo: AirportRepository) extends CreateAirportUseCase:

  override def create(command: CreateAirportCommand): IO[DomainError, Airport] =
    // No lookup before the insert: the repository already reports a duplicate natural key as
    // AirportAlreadyExists (via the table's UNIQUE constraint), which also covers a concurrent insert.
    val effect = repo.save(
      Airport(command.iataCode, command.icaoCode, command.name, command.city, command.latitude, command.longitude),
      command.countryCode
    )
    effect @@ ServiceAspect.logged(s"CreateAirportService.create(${command.iataCode.value})")

object CreateAirportService:
  val layer: URLayer[AirportRepository, CreateAirportUseCase] =
    ZLayer.fromFunction(new CreateAirportService(_))
