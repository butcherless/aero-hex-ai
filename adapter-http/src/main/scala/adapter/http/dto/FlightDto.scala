package adapter.http.dto

import domain.model.Flight
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
    .modify(_.code)(_.description("Primary airline flight code (e.g. UX9117)."))
    .modify(_.alias)(_.description("Alternative commercial flight code (e.g. AEA9117)."))
    .modify(_.schedDeparture)(_.description("Scheduled local departure time (HH:mm)."))
    .modify(_.schedArrival)(_.description("Scheduled local arrival time (HH:mm)."))
    .modify(_.routeId)(_.description("Identifier of the route this flight operates on.").format("uuid"))
    .modify(_.airlineIcao)(_.description("3-letter ICAO code of the operating airline."))
}
