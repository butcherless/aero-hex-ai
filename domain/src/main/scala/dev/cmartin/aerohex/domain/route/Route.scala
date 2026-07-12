package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode

import java.util.UUID

opaque type RouteId = UUID

object RouteId {
  def apply(value: UUID): RouteId        = value
  def generate: RouteId                  = UUID.randomUUID()
  extension (r: RouteId) def value: UUID = r
}

case class Route(
    id: RouteId,
    origin: IataCode,
    destination: IataCode,
    airlineIcao: IcaoCode,
    distanceKm: Int
)
