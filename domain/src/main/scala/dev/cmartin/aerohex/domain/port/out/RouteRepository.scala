package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Route, RouteId}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait RouteRepository {
  def findById(id: RouteId): IO[DomainError, Option[Route]]
  def findAll(pagination: Pagination): IO[DomainError, List[Route]]
  def save(route: Route): IO[DomainError, Route]
  def delete(id: RouteId): IO[DomainError, Unit]
}
