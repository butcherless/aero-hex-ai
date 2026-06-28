package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.OutboxEvent
import zio.IO

trait EventPublisher {
  def publish(event: OutboxEvent): IO[DomainError, Unit]
}
