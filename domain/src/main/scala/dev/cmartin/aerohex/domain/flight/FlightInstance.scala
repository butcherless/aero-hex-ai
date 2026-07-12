package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.aircraft.Registration
import java.time.LocalDateTime
import java.util.UUID

opaque type FlightInstanceId = UUID

object FlightInstanceId {
  def apply(value: UUID): FlightInstanceId        = value
  def generate: FlightInstanceId                  = UUID.randomUUID()
  extension (j: FlightInstanceId) def value: UUID = j
}

case class FlightInstance(
    id: FlightInstanceId,
    departureDate: LocalDateTime,
    arrivalDate: LocalDateTime,
    flightCode: FlightCode,
    registration: Registration
)
