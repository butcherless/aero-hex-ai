package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirlineUseCase {
  def findByIcao(icao: String): IO[DomainError, Airline]
  def findAll(pagination: Pagination): IO[DomainError, List[Airline]]
  def findAllUnbounded: IO[DomainError, List[Airline]]
}
