package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FlightRepository {
  def findByCode(code: FlightCode): IO[DomainError, Option[Flight]]
  def findAll(pagination: Pagination): IO[DomainError, List[Flight]]
  def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Flight]]
  def findAirlineByCode(code: FlightCode): IO[DomainError, Option[Airline]]
  def save(flight: Flight): IO[DomainError, Flight]
  def update(flight: Flight): IO[DomainError, Flight]
  def delete(code: FlightCode): IO[DomainError, Unit]
}
