package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.FlightInstance
import sttp.tapir.Schema

case class FlightInstanceDto(
    id: String,
    departureDate: String,
    arrivalDate: String,
    flightCode: String,
    registration: String
)

object FlightInstanceDto {
  def fromDomain(flightInstance: FlightInstance): FlightInstanceDto =
    FlightInstanceDto(
      id = flightInstance.id.value.toString,
      departureDate = flightInstance.departureDate.toString,
      arrivalDate = flightInstance.arrivalDate.toString,
      flightCode = flightInstance.flightCode.value,
      registration = flightInstance.registration.value
    )

  given Schema[FlightInstanceDto] = Schema.derived[FlightInstanceDto]
    .modify(_.id)(
      _.description("Unique flight instance identifier.").format("uuid").encodedExample(
        "b1c2d3e4-f5a6-7890-bcde-f01234567890"
      )
    )
    .modify(_.departureDate)(_.description(
      "Actual departure date and time (ISO 8601)."
    ).encodedExample("2024-06-28T15:23:00"))
    .modify(_.arrivalDate)(_.description(
      "Actual arrival date and time (ISO 8601)."
    ).encodedExample("2024-06-28T19:41:00"))
    .modify(_.flightCode)(_.description("Flight code this flight instance is an occurrence of.").encodedExample(
      "UX9117"
    ))
    .modify(_.registration)(_.description("Aircraft registration that operated this flight instance.").encodedExample(
      "EC-MIG"
    ))
}
