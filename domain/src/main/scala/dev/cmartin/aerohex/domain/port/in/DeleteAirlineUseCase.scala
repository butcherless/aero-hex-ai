package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.IcaoCode
import zio.IO

trait DeleteAirlineUseCase:
  def delete(icao: IcaoCode): IO[DomainError, Unit]
