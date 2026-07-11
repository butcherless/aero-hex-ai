package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Aircraft, Registration}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait AircraftRepository {
  def findByRegistration(registration: Registration): IO[DomainError, Option[Aircraft]]
  def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]]
  def save(aircraft: Aircraft): IO[DomainError, Aircraft]
  def update(aircraft: Aircraft): IO[DomainError, Aircraft]
  def delete(registration: Registration): IO[DomainError, Unit]
}
