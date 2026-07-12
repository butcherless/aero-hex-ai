package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.CountryCode
import zio.IO

import java.time.LocalDate

case class CreateAirlineCommand(
    icao: IcaoCode,
    name: String,
    foundationDate: LocalDate,
    countryCode: CountryCode
)

trait CreateAirlineUseCase:
  def create(command: CreateAirlineCommand): IO[DomainError, Airline]
