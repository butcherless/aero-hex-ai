package domain.port.in

import domain.error.DomainError
import domain.model.Flight
import shared.Pagination
import zio.IO

trait FindFlightUseCase {
  def findByCode(code: String): IO[DomainError, Flight]
  def findAll(pagination: Pagination): IO[DomainError, List[Flight]]
}
