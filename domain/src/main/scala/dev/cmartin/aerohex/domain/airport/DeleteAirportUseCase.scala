package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteAirportUseCase:
  def delete(iata: IataCode): IO[DomainError, Unit]
