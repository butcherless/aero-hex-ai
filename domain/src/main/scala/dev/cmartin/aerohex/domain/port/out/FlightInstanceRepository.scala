package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{FlightInstance, FlightInstanceId}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FlightInstanceRepository {
  def findById(id: FlightInstanceId): IO[DomainError, Option[FlightInstance]]
  def findAll(pagination: Pagination): IO[DomainError, List[FlightInstance]]
  def save(flightInstance: FlightInstance): IO[DomainError, FlightInstance]
  def delete(id: FlightInstanceId): IO[DomainError, Unit]
}
