package dev.cmartin.aerohex.adapter.http.health

import sttp.tapir.Schema

case class HealthStatus(status: String)

object HealthStatus:
  given Schema[HealthStatus] = Schema.derived[HealthStatus]
    .modify(_.status)(_.description("UP or DOWN.").encodedExample("UP"))
