package dev.cmartin.aerohex.infrastructure.messaging.kafka.config

import zio.Config

case class KafkaConfig(
    bootstrapServers: String,
    groupId: String
)

object KafkaConfig {

  // Keys under `kafka` in application.conf (plans/configuration-zio-config.md).
  val config: Config[KafkaConfig] =
    (Config.string("bootstrap-servers").withDefault("localhost:9092") ++
      Config.string("group-id").withDefault("aviation-consumer-group"))
      .map(KafkaConfig.apply)
      .nested("kafka")

  object Topics {
    val routeCreated   = "aviation.route.created"
    val airportUpdated = "aviation.airport.updated"
  }
}
