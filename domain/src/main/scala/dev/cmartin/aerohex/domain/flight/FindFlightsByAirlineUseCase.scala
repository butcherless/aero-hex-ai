package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindFlightsByAirlineUseCase:
  def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Flight]]
