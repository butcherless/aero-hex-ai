package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode

case class Route(
    origin: IataCode,
    destination: IataCode,
    distanceKm: Int
)
