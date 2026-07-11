package dev.cmartin.aerohex.domain.port.in

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, CountryCode, IcaoCode}
import zio.IO

import java.time.LocalDate

case class UpdateAirlineCommand(
    icao: IcaoCode,
    name: String,
    foundationDate: LocalDate,
    countryCode: CountryCode
)

trait UpdateAirlineUseCase:
  def update(command: UpdateAirlineCommand): IO[DomainError, Airline]
