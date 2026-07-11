package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Aircraft, IcaoCode, Registration}
import zio.IO

case class CreateAircraftCommand(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: IcaoCode
)

trait CreateAircraftUseCase:
  def create(command: CreateAircraftCommand): IO[DomainError, Aircraft]
