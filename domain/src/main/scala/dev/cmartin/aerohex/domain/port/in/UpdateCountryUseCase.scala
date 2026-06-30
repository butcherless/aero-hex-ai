package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import zio.IO

case class UpdateCountryCommand(code: CountryCode, name: String)

trait UpdateCountryUseCase:
  def update(command: UpdateCountryCommand): IO[DomainError, Country]
