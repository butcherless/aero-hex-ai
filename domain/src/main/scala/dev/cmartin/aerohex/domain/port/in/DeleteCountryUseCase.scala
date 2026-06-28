package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteCountryUseCase:
  def delete(code: String): IO[DomainError, Unit]
