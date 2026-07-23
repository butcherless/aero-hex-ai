package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class UpdateAirlineCommand(
    icao: AirlineIcaoCode,
    name: String,
    alias: Option[String],
    callsign: Option[String],
    countryCode: CountryCode
)

trait UpdateAirlineUseCase:
  def update(command: UpdateAirlineCommand): IO[DomainError, Airline]
