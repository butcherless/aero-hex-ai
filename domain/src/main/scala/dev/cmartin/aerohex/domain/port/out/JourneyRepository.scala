package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Journey, JourneyId}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait JourneyRepository {
  def findById(id: JourneyId): IO[DomainError, Option[Journey]]
  def findAll(pagination: Pagination): IO[DomainError, List[Journey]]
  def save(journey: Journey): IO[DomainError, Journey]
  def delete(id: JourneyId): IO[DomainError, Unit]
}
