package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{OutboxEvent, OutboxEventId}
import zio.IO

trait OutboxRepository {
  def save(event: OutboxEvent): IO[DomainError, OutboxEvent]
  def findUnpublished(limit: Int): IO[DomainError, List[OutboxEvent]]
  def markPublished(id: OutboxEventId): IO[DomainError, Unit]
}
