package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode}
import zio.IO

case class UpdateAirportCommand(
    iataCode: IataCode,
    icaoCode: String,
    name: String,
    city: String,
    countryCode: CountryCode
)

trait UpdateAirportUseCase:
  def update(command: UpdateAirportCommand): IO[DomainError, Airport]
