package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.{CreateAirlineCommand, CreateAirlineUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class CreateAirlineService(repo: AirlineRepository) extends CreateAirlineUseCase:

  override def create(command: CreateAirlineCommand): IO[DomainError, Airline] =
    // No lookup before the insert: the repository already reports a duplicate natural key as
    // AirlineAlreadyExists (via the table's UNIQUE constraint), which also covers a concurrent insert.
    val effect = repo.save(
      Airline(command.icao, command.name, command.alias, command.callsign, command.iata),
      command.countryCode
    )
    effect @@ ServiceAspect.logged(s"CreateAirlineService.create(${command.icao.value})")

object CreateAirlineService:
  val layer: URLayer[AirlineRepository, CreateAirlineUseCase] =
    ZLayer.fromFunction(new CreateAirlineService(_))
