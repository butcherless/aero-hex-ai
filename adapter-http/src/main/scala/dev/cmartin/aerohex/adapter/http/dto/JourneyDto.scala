package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.Journey
import sttp.tapir.Schema

case class JourneyDto(
    id: String,
    departureDate: String,
    arrivalDate: String,
    flightCode: String,
    registration: String
)

object JourneyDto {
  def fromDomain(journey: Journey): JourneyDto =
    JourneyDto(
      id = journey.id.value.toString,
      departureDate = journey.departureDate.toString,
      arrivalDate = journey.arrivalDate.toString,
      flightCode = journey.flightCode.value,
      registration = journey.registration.value
    )

  given Schema[JourneyDto] = Schema.derived[JourneyDto]
    .modify(_.id)(
      _.description("Unique journey identifier.").format("uuid").encodedExample("b1c2d3e4-f5a6-7890-bcde-f01234567890")
    )
    .modify(_.departureDate)(_.description(
      "Actual departure date and time (ISO 8601)."
    ).encodedExample("2024-06-28T15:23:00"))
    .modify(_.arrivalDate)(_.description(
      "Actual arrival date and time (ISO 8601)."
    ).encodedExample("2024-06-28T19:41:00"))
    .modify(_.flightCode)(_.description("Flight code this journey is an instance of.").encodedExample("UX9117"))
    .modify(_.registration)(_.description("Aircraft registration that operated this journey.").encodedExample("EC-MIG"))
}
