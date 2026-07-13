package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airline.{Airline, IcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

// Route<->Airline is many-to-many: a route is operated by several airlines, and an airline
// operates several routes. Backed by a route_airlines join table; kept as its own port rather
// than folded into RouteRepository/AirlineRepository since it's a distinct persistence concern
// (association add/remove) on top of both aggregates' own CRUD.
trait RouteAirlineRepository {
  def associate(origin: IataCode, destination: IataCode, icao: IcaoCode): IO[DomainError, Unit]
  def disassociate(origin: IataCode, destination: IataCode, icao: IcaoCode): IO[DomainError, Unit]
  def findAirlines(origin: IataCode, destination: IataCode): IO[DomainError, List[Airline]]
  def findRoutes(icao: IcaoCode, pagination: Pagination): IO[DomainError, List[Route]]
}
