package application.service

import domain.error.DomainError
import domain.error.DomainError.JourneyNotFound
import domain.model.{Journey, JourneyId}
import domain.port.in.FindJourneyUseCase
import domain.port.out.JourneyRepository
import shared.Pagination
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
