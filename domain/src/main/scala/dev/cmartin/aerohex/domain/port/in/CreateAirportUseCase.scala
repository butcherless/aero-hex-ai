package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode, IcaoCode}
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
