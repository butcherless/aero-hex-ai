package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindRouteUseCase {
  def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, RouteWithAirportNames]
  def findAll(pagination: Pagination): UIO[List[RouteWithAirportNames]]
  def findAllUnbounded: UIO[List[Route]]
  def findByOrigin(origin: IataCode, pagination: Pagination): IO[DomainError, List[RouteWithAirportNames]]
  def findByDestination(
      destination: IataCode,
      pagination: Pagination
  ): IO[DomainError, List[RouteWithAirportNames]]
}
