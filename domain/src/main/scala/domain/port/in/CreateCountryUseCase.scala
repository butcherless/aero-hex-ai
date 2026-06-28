package domain.port.in

import domain.error.DomainError
import domain.model.Country
import zio.IO

case class CreateCountryCommand(code: String, name: String)

trait CreateCountryUseCase:
  def create(command: CreateCountryCommand): IO[DomainError, Country]
