package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.country.CountryCode
import zio.IO

case class UpdateAirportCommand(
    iataCode: IataCode,
    icaoCode: IcaoCode,
    name: String,
    city: String,
    countryCode: CountryCode
)

trait UpdateAirportUseCase:
  def update(command: UpdateAirportCommand): IO[DomainError, Airport]
