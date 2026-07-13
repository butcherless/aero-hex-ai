package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.IcaoCode
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
    airlineIcao: IcaoCode
)

trait UpdateFlightUseCase:
  def update(command: UpdateFlightCommand): IO[DomainError, Flight]
