package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Airline
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirlineUseCase {
  def findByIcao(icao: String): IO[DomainError, Airline]
  def findAll(pagination: Pagination): IO[DomainError, List[Airline]]
}
