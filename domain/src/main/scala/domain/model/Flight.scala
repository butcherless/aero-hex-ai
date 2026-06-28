package domain.model

import java.time.LocalTime

opaque type FlightCode = String

object FlightCode {
  def apply(value: String): FlightCode        = value
  extension (f: FlightCode) def value: String = f
}

case class Flight(
    code: FlightCode,
    alias: Option[String],
    schedDeparture: LocalTime,
    schedArrival: LocalTime,
    routeId: RouteId,
    airlineIcao: IcaoCode
)
