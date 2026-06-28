package domain.port.in

import domain.error.DomainError
import domain.model.Aircraft
import shared.Pagination
import zio.IO

trait FindAircraftUseCase {
  def findByRegistration(registration: String): IO[DomainError, Aircraft]
  def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]]
}
