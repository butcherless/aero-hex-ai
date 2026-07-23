package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.IO

case class CreateAirlineCommand(
    icao: AirlineIcaoCode,
    name: String,
    alias: Option[String],
    callsign: Option[String],
    countryCode: CountryCode
)

trait CreateAirlineUseCase:
  def create(command: CreateAirlineCommand): IO[DomainError, Airline]
