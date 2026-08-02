package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode

case class Route(
    origin: IataCode,
    destination: IataCode,
    distanceKm: Int
)

/** A `Route` paired with its origin/destination airports' human-readable names
  * — a read-time enrichment for the list/search endpoint only.
  * `save`/`update`/`findAllUnbounded` and the route<->airline association paths
  * continue to use `Route` alone; names aren't part of a route's identity or
  * persisted state.
  */
case class RouteWithAirportNames(
    route: Route,
    originAirportName: String,
    destinationAirportName: String
)
