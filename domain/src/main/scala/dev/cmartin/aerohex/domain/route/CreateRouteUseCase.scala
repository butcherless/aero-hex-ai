package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class CreateRouteCommand(
    originIata: String,
    destinationIata: String,
    distanceKm: Int
)

trait CreateRouteUseCase {
  def create(command: CreateRouteCommand): IO[DomainError, Route]
}
