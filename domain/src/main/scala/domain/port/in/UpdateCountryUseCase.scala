package domain.port.in

import domain.error.DomainError
import domain.model.Country
import zio.IO

case class UpdateCountryCommand(code: String, name: String)

trait UpdateCountryUseCase:
  def update(command: UpdateCountryCommand): IO[DomainError, Country]
