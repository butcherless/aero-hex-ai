package application.service

import domain.error.DomainError
import domain.error.DomainError.AircraftNotFound
import domain.model.{Aircraft, Registration}
import domain.port.in.FindAircraftUseCase
import domain.port.out.AircraftRepository
import shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

final class FindAircraftService(repo: AircraftRepository) extends FindAircraftUseCase {

  override def findByRegistration(registration: String): IO[DomainError, Aircraft] =
    repo.findByRegistration(Registration(registration)).flatMap {
      case Some(a) => ZIO.succeed(a)
      case None    => ZIO.fail(AircraftNotFound(registration))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]] =
    repo.findAll(pagination)
}

object FindAircraftService {
  val layer: URLayer[AircraftRepository, FindAircraftUseCase] =
    ZLayer.fromFunction(new FindAircraftService(_))
}
