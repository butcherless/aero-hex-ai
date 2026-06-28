package domain.port.out

import domain.error.DomainError
import domain.model.{Route, RouteId}
import shared.Pagination
import zio.IO

trait RouteRepository {
  def findById(id: RouteId): IO[DomainError, Option[Route]]
  def findAll(pagination: Pagination): IO[DomainError, List[Route]]
  def save(route: Route): IO[DomainError, Route]
  def delete(id: RouteId): IO[DomainError, Unit]
}
