package dev.cmartin.aerohex.infrastructure.messaging.kafka.route

import zio.kafka.serde.Serde

case class RouteCreatedEvent(
    originIata: String,
    destinationIata: String,
    distanceKm: Int
)

object RouteEventCodec {
  // TODO: implement with Circe + ZIO Kafka 3.x Serde API
  val routeCreatedSerde: Serde[Any, RouteCreatedEvent] = ???
}
