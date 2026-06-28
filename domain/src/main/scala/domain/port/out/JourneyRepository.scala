package domain.port.out

import domain.error.DomainError
import domain.model.{Journey, JourneyId}
import shared.Pagination
import zio.IO

trait JourneyRepository {
  def findById(id: JourneyId): IO[DomainError, Option[Journey]]
  def findAll(pagination: Pagination): IO[DomainError, List[Journey]]
  def save(journey: Journey): IO[DomainError, Journey]
  def delete(id: JourneyId): IO[DomainError, Unit]
}
