package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class CreateAirportCommand(
    iataCode: IataCode,
    icaoCode: IcaoCode,
    name: String,
    city: String,
    countryCode: CountryCode
)

trait CreateAirportUseCase:
  def create(command: CreateAirportCommand): IO[DomainError, Airport]
