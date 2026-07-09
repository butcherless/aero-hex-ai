package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.FlightInstance
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindFlightInstanceUseCase {
  def findById(id: String): IO[DomainError, FlightInstance]
  def findAll(pagination: Pagination): IO[DomainError, List[FlightInstance]]
}
