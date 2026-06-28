package domain.model

import java.time.LocalDateTime
import java.util.UUID

opaque type JourneyId = UUID

object JourneyId {
  def apply(value: UUID): JourneyId        = value
  def generate: JourneyId                  = UUID.randomUUID()
  extension (j: JourneyId) def value: UUID = j
}

case class Journey(
    id: JourneyId,
    departureDate: LocalDateTime,
    arrivalDate: LocalDateTime,
    flightCode: FlightCode,
    registration: Registration
)
