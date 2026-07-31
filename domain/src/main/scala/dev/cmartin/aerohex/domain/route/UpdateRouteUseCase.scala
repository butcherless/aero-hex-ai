package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class UpdateRouteCommand(
    originIata: String,
    destinationIata: String,
    distanceKm: Int
)

trait UpdateRouteUseCase {
  def update(command: UpdateRouteCommand): IO[DomainError, Route]
}
