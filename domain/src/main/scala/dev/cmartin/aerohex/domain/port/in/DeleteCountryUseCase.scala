package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.CountryCode
import zio.IO

trait DeleteCountryUseCase:
  def delete(code: CountryCode): IO[DomainError, Unit]
