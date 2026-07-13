package dev.cmartin.aerohex.application.aircraft

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.aircraft.AircraftRepository
import dev.cmartin.aerohex.domain.aircraft.FindAircraftUseCase
import dev.cmartin.aerohex.domain.aircraft.{Aircraft, Registration}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AircraftNotFound
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAircraftService(repo: AircraftRepository) extends FindAircraftUseCase {

  override def findByRegistration(registration: String): IO[DomainError, Aircraft] =
    repo.findByRegistration(Registration.unsafeMake(registration)).someOrFail(AircraftNotFound(registration)) @@
      ServiceAspect.logged(s"FindAircraftService.findByRegistration($registration)")

  override def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindAircraftService.findAll")
}

object FindAircraftService {
  val layer: URLayer[AircraftRepository, FindAircraftUseCase] =
    ZLayer.fromFunction(new FindAircraftService(_))
}
