package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindRouteUseCase {
  def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Route]
  def findAll(pagination: Pagination): IO[DomainError, List[Route]]
  def findAllUnbounded: IO[DomainError, List[Route]]
  def findByOrigin(origin: IataCode, pagination: Pagination): IO[DomainError, List[Route]]
  def findByDestination(destination: IataCode, pagination: Pagination): IO[DomainError, List[Route]]
}
