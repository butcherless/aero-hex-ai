package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.IataCode
import zio.IO

trait DeleteAirportUseCase:
  def delete(iata: IataCode): IO[DomainError, Unit]
