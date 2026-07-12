package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.domain.flight.Flight
import sttp.tapir.Schema

case class FlightDto(
    code: String,
    alias: Option[String],
    schedDeparture: String,
    schedArrival: String,
    routeId: String,
    airlineIcao: String
)

object FlightDto {
  def fromDomain(flight: Flight): FlightDto =
    FlightDto(
      code = flight.code.value,
      alias = flight.alias,
      schedDeparture = flight.schedDeparture.toString,
      schedArrival = flight.schedArrival.toString,
      routeId = flight.routeId.value.toString,
      airlineIcao = flight.airlineIcao.value
    )

  given Schema[FlightDto] = Schema.derived[FlightDto]
    .modify(_.code)(_.description("Primary airline flight code.").encodedExample("UX9117"))
    .modify(_.alias)(_.description("Alternative commercial flight code.").encodedExample("AEA9117"))
    .modify(_.schedDeparture)(_.description("Scheduled local departure time (HH:mm).").encodedExample("07:05"))
    .modify(_.schedArrival)(_.description("Scheduled local arrival time (HH:mm).").encodedExample("08:55"))
    .modify(_.routeId)(_.description(
      "Identifier of the route this flight operates on."
    ).format("uuid").encodedExample("a0b1c2d3-e4f5-6789-abcd-ef0123456789"))
    .modify(_.airlineIcao)(_.description("3-letter ICAO code of the operating airline.").encodedExample("AEA"))
}
