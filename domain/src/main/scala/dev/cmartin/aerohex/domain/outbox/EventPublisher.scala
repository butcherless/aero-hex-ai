package dev.cmartin.aerohex.domain.outbox

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait EventPublisher {
  def publish(event: OutboxEvent): IO[DomainError, Unit]
}
