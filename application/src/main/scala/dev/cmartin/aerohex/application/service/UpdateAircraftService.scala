package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Aircraft
import dev.cmartin.aerohex.domain.port.in.{UpdateAircraftCommand, UpdateAircraftUseCase}
import dev.cmartin.aerohex.domain.port.out.AircraftRepository
import zio.{IO, URLayer, ZLayer}

final class UpdateAircraftService(repo: AircraftRepository) extends UpdateAircraftUseCase:

  override def update(command: UpdateAircraftCommand): IO[DomainError, Aircraft] =
    repo.update(Aircraft(command.registration, command.typeCode, command.description, command.airlineIcao)) @@
      ServiceAspect.logged(s"UpdateAircraftService.update(${command.registration.value})")

object UpdateAircraftService:
  val layer: URLayer[AircraftRepository, UpdateAircraftUseCase] =
    ZLayer.fromFunction(new UpdateAircraftService(_))
