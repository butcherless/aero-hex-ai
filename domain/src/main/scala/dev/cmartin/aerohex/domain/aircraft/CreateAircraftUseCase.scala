package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class CreateAircraftCommand(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: IcaoCode
)

trait CreateAircraftUseCase:
  def create(command: CreateAircraftCommand): IO[DomainError, Aircraft]
