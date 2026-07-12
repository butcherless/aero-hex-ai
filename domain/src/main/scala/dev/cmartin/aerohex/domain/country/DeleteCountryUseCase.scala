package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DeleteCountryUseCase:
  def delete(code: CountryCode): IO[DomainError, Unit]
