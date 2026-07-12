package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class UpdateCountryCommand(code: CountryCode, name: String)

trait UpdateCountryUseCase:
  def update(command: UpdateCountryCommand): IO[DomainError, Country]
