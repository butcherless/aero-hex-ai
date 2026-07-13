package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait RouteRepository {
  def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Option[Route]]
  def findAll(pagination: Pagination): IO[DomainError, List[Route]]
  def save(route: Route): IO[DomainError, Route]
  def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit]
}
