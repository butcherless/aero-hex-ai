package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Aircraft
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAircraftUseCase {
  def findByRegistration(registration: String): IO[DomainError, Aircraft]
  def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]]
}
