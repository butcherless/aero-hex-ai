package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.IO

trait FindRoutesByAirlineUseCase {
  def findByAirline(airlineIcao: String, pagination: Pagination): IO[DomainError, List[Route]]
}
