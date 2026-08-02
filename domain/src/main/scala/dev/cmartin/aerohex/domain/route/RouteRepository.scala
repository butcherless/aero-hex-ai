package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait RouteRepository {
  def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Option[RouteWithAirportNames]]
  def findAll(pagination: Pagination): IO[DomainError, List[RouteWithAirportNames]]
  def findAllUnbounded: IO[DomainError, List[Route]]
  def findByOrigin(origin: IataCode, pagination: Pagination): IO[DomainError, List[RouteWithAirportNames]]
  def findByDestination(
      destination: IataCode,
      pagination: Pagination
  ): IO[DomainError, List[RouteWithAirportNames]]
  def save(route: Route): IO[DomainError, Route]
  def update(route: Route): IO[DomainError, Route]
  def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit]
}
