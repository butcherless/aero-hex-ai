package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindAircraftUseCase {
  def findByRegistration(registration: String): IO[DomainError, Aircraft]
  def findAll(pagination: Pagination): UIO[List[Aircraft]]
}
