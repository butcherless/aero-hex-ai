package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import zio.IO

case class CreateCountryCommand(code: CountryCode, name: String)

trait CreateCountryUseCase:
  def create(command: CreateCountryCommand): IO[DomainError, Country]
