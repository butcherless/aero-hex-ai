package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import java.time.LocalDate
import zio.IO

case class CreateAirlineCommand(
    icao: IcaoCode,
    name: String,
    foundationDate: LocalDate,
    countryCode: CountryCode
)

trait CreateAirlineUseCase:
  def create(command: CreateAirlineCommand): IO[DomainError, Airline]
