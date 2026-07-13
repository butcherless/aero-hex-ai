package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import java.time.LocalTime
import zio.IO

case class UpdateFlightCommand(
    code: FlightCode,
    alias: Option[String],
    schedDeparture: LocalTime,
    schedArrival: LocalTime,
    origin: IataCode,
    destination: IataCode,
    airlineIcao: AirlineIcaoCode
)

trait UpdateFlightUseCase:
  def update(command: UpdateFlightCommand): IO[DomainError, Flight]
