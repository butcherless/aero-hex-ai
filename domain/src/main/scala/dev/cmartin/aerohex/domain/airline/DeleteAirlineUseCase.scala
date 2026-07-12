package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteAirlineUseCase:
  def delete(icao: IcaoCode): IO[DomainError, Unit]
