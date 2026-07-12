package dev.cmartin.aerohex.application.aircraft

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.aircraft.Aircraft
import dev.cmartin.aerohex.domain.aircraft.AircraftRepository
import dev.cmartin.aerohex.domain.aircraft.{CreateAircraftCommand, CreateAircraftUseCase}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, URLayer, ZIO, ZLayer}

final class CreateAircraftService(repo: AircraftRepository) extends CreateAircraftUseCase:

  override def create(command: CreateAircraftCommand): IO[DomainError, Aircraft] =
    val effect = repo.findByRegistration(command.registration).flatMap:
      case Some(_) => ZIO.fail(DomainError.AircraftAlreadyExists(command.registration.value))
      case None    =>
        repo.save(Aircraft(command.registration, command.typeCode, command.description, command.airlineIcao))
    effect @@ ServiceAspect.logged(s"CreateAircraftService.create(${command.registration.value})")

object CreateAircraftService:
  val layer: URLayer[AircraftRepository, CreateAircraftUseCase] =
    ZLayer.fromFunction(new CreateAircraftService(_))
