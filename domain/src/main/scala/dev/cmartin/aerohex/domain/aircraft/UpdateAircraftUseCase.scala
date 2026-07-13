package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class UpdateAircraftCommand(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: AirlineIcaoCode
)

trait UpdateAircraftUseCase:
  def update(command: UpdateAircraftCommand): IO[DomainError, Aircraft]
