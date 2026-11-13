package dev.cmartin.aerohex.adapter.http.health

import io.circe.Codec
import sttp.tapir.Schema

case class HealthStatus(status: String) derives Codec.AsObject

object HealthStatus:
  given Schema[HealthStatus] = Schema.derived[HealthStatus]
    .modify(_.status)(_.description("UP or DOWN.").encodedExample("UP"))
