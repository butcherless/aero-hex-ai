package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.FlightInstanceNotFound
import dev.cmartin.aerohex.domain.flight.FindFlightInstanceUseCase
import dev.cmartin.aerohex.domain.flight.FlightInstanceRepository
import dev.cmartin.aerohex.domain.flight.{FlightInstance, FlightInstanceId}
import dev.cmartin.aerohex.shared.Pagination
import java.util.UUID
import zio.{IO, URLayer, ZIO, ZLayer}

final class FindFlightInstanceService(repo: FlightInstanceRepository) extends FindFlightInstanceUseCase {

  override def findById(id: String): IO[DomainError, FlightInstance] =
    scala.util.Try(UUID.fromString(id)).toOption match {
      case Some(uuid) => repo.findById(FlightInstanceId(uuid)).someOrFail(FlightInstanceNotFound(id))
      case None       => ZIO.fail(FlightInstanceNotFound(id))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[FlightInstance]] =
    repo.findAll(pagination)
}

object FindFlightInstanceService {
  val layer: URLayer[FlightInstanceRepository, FindFlightInstanceUseCase] =
    ZLayer.fromFunction(new FindFlightInstanceService(_))
}
