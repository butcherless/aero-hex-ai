package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Airport
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindAirportUseCase {
  def findByIata(iata: String): IO[DomainError, Airport]
  def findAll(pagination: Pagination): IO[DomainError, List[Airport]]
  def searchByName(query: String): IO[DomainError, List[Airport]]
}
