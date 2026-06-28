package domain.port.out

import domain.error.DomainError
import domain.model.{Airport, IataCode}
import shared.Pagination
import zio.IO

trait AirportRepository {
  def findByIata(iata: IataCode): IO[DomainError, Option[Airport]]
  def findAll(pagination: Pagination): IO[DomainError, List[Airport]]
  def save(airport: Airport): IO[DomainError, Airport]
  def delete(iata: IataCode): IO[DomainError, Unit]
}
