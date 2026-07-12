package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteAircraftUseCase:
  def delete(registration: Registration): IO[DomainError, Unit]
