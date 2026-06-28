package dev.cmartin.aerohex.application.service

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.JourneyNotFound
import dev.cmartin.aerohex.domain.model.{Journey, JourneyId}
import dev.cmartin.aerohex.domain.port.in.FindJourneyUseCase
import dev.cmartin.aerohex.domain.port.out.JourneyRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO, URLayer, ZLayer}

import java.util.UUID

final class FindJourneyService(repo: JourneyRepository) extends FindJourneyUseCase {

  override def findById(id: String): IO[DomainError, Journey] =
    scala.util.Try(UUID.fromString(id)).toOption match {
      case Some(uuid) =>
        repo.findById(JourneyId(uuid)).flatMap {
          case Some(j) => ZIO.succeed(j)
          case None    => ZIO.fail(JourneyNotFound(id))
        }
      case None       => ZIO.fail(JourneyNotFound(id))
    }

  override def findAll(pagination: Pagination): IO[DomainError, List[Journey]] =
    repo.findAll(pagination)
}

object FindJourneyService {
  val layer: URLayer[JourneyRepository, FindJourneyUseCase] =
    ZLayer.fromFunction(new FindJourneyService(_))
}
