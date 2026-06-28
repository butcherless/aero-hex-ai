package domain.port.in

import domain.error.DomainError
import zio.IO

trait DeleteCountryUseCase:
  def delete(code: String): IO[DomainError, Unit]
