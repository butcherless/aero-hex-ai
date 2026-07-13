package dev.cmartin.aerohex.domain.route

import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

trait DisassociateAirlineUseCase {
  def disassociate(originIata: String, destinationIata: String, airlineIcao: String): IO[DomainError, Unit]
}
