package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait FindAirlineForFlightUseCase:
  def findAirline(code: FlightCode): IO[DomainError, Airline]
