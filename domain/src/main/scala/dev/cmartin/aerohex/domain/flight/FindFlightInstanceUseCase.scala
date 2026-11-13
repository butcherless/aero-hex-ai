package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindFlightInstanceUseCase {
  def findById(id: String): IO[DomainError, FlightInstance]
  def findAll(pagination: Pagination): UIO[List[FlightInstance]]
}
