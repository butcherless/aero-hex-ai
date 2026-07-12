package dev.cmartin.aerohex.infrastructure.messaging.kafka.route

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.outbox.{EventPublisher, OutboxEvent}
import dev.cmartin.aerohex.infrastructure.messaging.kafka.config.KafkaConfig
import scala.annotation.unused
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.{IO, TaskLayer, URLayer, ZIO, ZLayer}

// `producer` is unused until publish() is implemented for real (see CLAUDE.md's
// Pending implementations table) — @unused silences -Wunused until then.
final class RouteEventProducer(@unused producer: Producer) extends EventPublisher {

  override def publish(event: OutboxEvent): IO[DomainError, Unit] =
    // TODO: implement per event type using ZIO Kafka 3.x API
    ZIO.logInfo(s"Publishing event: ${event.eventType} for ${event.aggregateId}").unit
}

object RouteEventProducer {

  val layer: URLayer[Producer, EventPublisher] =
    ZLayer.fromFunction(new RouteEventProducer(_))

  val producerLayer: TaskLayer[Producer] =
    ZLayer.scoped {
      Producer.make(
        ProducerSettings(List(KafkaConfig.default.bootstrapServers))
      )
    }
}
