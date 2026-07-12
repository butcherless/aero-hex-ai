package dev.cmartin.aerohex.domain.country

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class CreateCountryCommand(code: CountryCode, name: String)

trait CreateCountryUseCase:
  def create(command: CreateCountryCommand): IO[DomainError, Country]
