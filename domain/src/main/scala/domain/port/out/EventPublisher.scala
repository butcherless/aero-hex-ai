package domain.port.out

import domain.error.DomainError
import domain.model.OutboxEvent
import zio.IO

trait EventPublisher {
  def publish(event: OutboxEvent): IO[DomainError, Unit]
}
