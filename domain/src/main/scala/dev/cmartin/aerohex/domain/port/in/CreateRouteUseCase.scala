package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Route
import zio.IO

case class CreateRouteCommand(
    originIata: String,
    destinationIata: String,
    airlineIcao: String,
    distanceKm: Int
)

trait CreateRouteUseCase {
  def create(command: CreateRouteCommand): IO[DomainError, Route]
}
