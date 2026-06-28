package domain.port.out

import domain.error.DomainError
import domain.model.{Airline, IcaoCode}
import shared.Pagination
import zio.IO

trait AirlineRepository {
  def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airline]]
  def save(airline: Airline): IO[DomainError, Airline]
  def delete(icao: IcaoCode): IO[DomainError, Unit]
}
