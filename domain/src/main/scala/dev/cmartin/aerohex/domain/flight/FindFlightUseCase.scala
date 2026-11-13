package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO}

trait FindFlightUseCase {
  def findByCode(code: String): IO[DomainError, Flight]
  def findAll(pagination: Pagination): UIO[List[Flight]]
}
