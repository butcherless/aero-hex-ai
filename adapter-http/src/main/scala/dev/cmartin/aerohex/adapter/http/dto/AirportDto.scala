package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.Airport
import sttp.tapir.Schema
import sttp.tapir.Validator

case class AirportDto(iata: String, icaoCode: String, name: String, city: String, countryCode: String)

object AirportDto {
  def fromDomain(airport: Airport): AirportDto =
    AirportDto(
      iata = airport.iata.value,
      icaoCode = airport.icaoCode,
      name = airport.name,
      city = airport.city,
      countryCode = airport.countryCode.value
    )

  given Schema[AirportDto] = Schema.derived[AirportDto]
    .modify(_.iata)(
      _.description("3-letter IATA airport code.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("MAD")
    )
    .modify(_.icaoCode)(
      _.description("4-letter ICAO airport code.")
        .validate(Validator.minLength(4))
        .validate(Validator.maxLength(4))
        .encodedExample("LEMD")
    )
    .modify(_.name)(_.description("Full airport name.").encodedExample("Adolfo Suárez Madrid-Barajas"))
    .modify(_.city)(_.description("City served by the airport.").encodedExample("Madrid"))
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .encodedExample("ES")
    )
}
