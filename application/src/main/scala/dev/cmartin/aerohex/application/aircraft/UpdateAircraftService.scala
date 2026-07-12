package dev.cmartin.aerohex.application.aircraft

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.aircraft.Aircraft
import dev.cmartin.aerohex.domain.aircraft.AircraftRepository
import dev.cmartin.aerohex.domain.aircraft.{UpdateAircraftCommand, UpdateAircraftUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZLayer}

final class UpdateAircraftService(repo: AircraftRepository) extends UpdateAircraftUseCase:

  override def update(command: UpdateAircraftCommand): IO[DomainError, Aircraft] =
    repo.update(Aircraft(command.registration, command.typeCode, command.description, command.airlineIcao)) @@
      ServiceAspect.logged(s"UpdateAircraftService.update(${command.registration.value})")

object UpdateAircraftService:
  val layer: URLayer[AircraftRepository, UpdateAircraftUseCase] =
    ZLayer.fromFunction(new UpdateAircraftService(_))
