package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import java.time.LocalDate
import zio.IO

case class UpdateAirlineCommand(
    icao: IcaoCode,
    name: String,
    foundationDate: LocalDate,
    countryCode: CountryCode
)

trait UpdateAirlineUseCase:
  def update(command: UpdateAirlineCommand): IO[DomainError, Airline]
