package dev.cmartin.aerohex.domain.port.out

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, IcaoCode}
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait AirlineRepository {
  def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airline]]
  def save(airline: Airline): IO[DomainError, Airline]
  def delete(icao: IcaoCode): IO[DomainError, Unit]
}
