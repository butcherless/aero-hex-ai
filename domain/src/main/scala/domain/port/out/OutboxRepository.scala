package domain.port.out

import domain.error.DomainError
import domain.model.{OutboxEvent, OutboxEventId}
import zio.IO

trait OutboxRepository {
  def save(event: OutboxEvent): IO[DomainError, OutboxEvent]
  def findUnpublished(limit: Int): IO[DomainError, List[OutboxEvent]]
  def markPublished(id: OutboxEventId): IO[DomainError, Unit]
}
