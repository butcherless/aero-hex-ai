package domain.port.out

import domain.error.DomainError
import domain.model.{Aircraft, Registration}
import shared.Pagination
import zio.IO

trait AircraftRepository {
  def findByRegistration(registration: Registration): IO[DomainError, Option[Aircraft]]
  def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]]
  def save(aircraft: Aircraft): IO[DomainError, Aircraft]
  def delete(registration: Registration): IO[DomainError, Unit]
}
