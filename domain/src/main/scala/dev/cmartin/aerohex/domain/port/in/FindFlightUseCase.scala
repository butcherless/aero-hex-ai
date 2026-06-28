package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Flight
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindFlightUseCase {
  def findByCode(code: String): IO[DomainError, Flight]
  def findAll(pagination: Pagination): IO[DomainError, List[Flight]]
}
