package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteRouteUseCase {
  def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit]
}
