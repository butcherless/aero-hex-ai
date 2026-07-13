package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteFlightUseCase:
  def delete(code: FlightCode): IO[DomainError, Unit]
