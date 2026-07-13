package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class UpdateAirportCommand(
    iataCode: IataCode,
    icaoCode: AirportIcaoCode,
    name: String,
    city: String,
    countryCode: CountryCode
)

trait UpdateAirportUseCase:
  def update(command: UpdateAirportCommand): IO[DomainError, Airport]
