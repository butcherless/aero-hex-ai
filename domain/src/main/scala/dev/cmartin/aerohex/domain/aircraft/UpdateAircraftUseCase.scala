package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.IcaoCode
import zio.IO

case class UpdateAircraftCommand(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: IcaoCode
)

trait UpdateAircraftUseCase:
  def update(command: UpdateAircraftCommand): IO[DomainError, Aircraft]
