package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Registration
import dev.cmartin.aerohex.domain.port.in.DeleteAircraftUseCase
import dev.cmartin.aerohex.domain.port.out.AircraftRepository
import zio.{IO, URLayer, ZLayer}

final class DeleteAircraftService(repo: AircraftRepository) extends DeleteAircraftUseCase:

  override def delete(registration: Registration): IO[DomainError, Unit] =
    repo.delete(registration) @@ ServiceAspect.logged(s"DeleteAircraftService.delete(${registration.value})")

object DeleteAircraftService:
  val layer: URLayer[AircraftRepository, DeleteAircraftUseCase] =
    ZLayer.fromFunction(new DeleteAircraftService(_))
