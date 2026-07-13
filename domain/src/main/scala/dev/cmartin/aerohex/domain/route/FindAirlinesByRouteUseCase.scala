package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait FindAirlinesByRouteUseCase {
  def findByRoute(originIata: String, destinationIata: String): IO[DomainError, List[Airline]]
}
