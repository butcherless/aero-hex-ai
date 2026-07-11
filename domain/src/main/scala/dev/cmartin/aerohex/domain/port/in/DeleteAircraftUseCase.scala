package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.Registration
import zio.IO

trait DeleteAircraftUseCase:
  def delete(registration: Registration): IO[DomainError, Unit]
