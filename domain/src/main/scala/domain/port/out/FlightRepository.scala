package domain.port.out

import domain.error.DomainError
import domain.model.{Flight, FlightCode}
import shared.Pagination
import zio.IO

trait FlightRepository {
  def findByCode(code: FlightCode): IO[DomainError, Option[Flight]]
  def findAll(pagination: Pagination): IO[DomainError, List[Flight]]
  def save(flight: Flight): IO[DomainError, Flight]
  def delete(code: FlightCode): IO[DomainError, Unit]
}
