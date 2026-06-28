package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AircraftNotFound
import dev.cmartin.aerohex.domain.model.{Aircraft, Registration}
import dev.cmartin.aerohex.domain.port.in.FindAircraftUseCase
import dev.cmartin.aerohex.domain.port.out.AircraftRepository
import dev.cmartin.aerohex.shared.Pagination
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
