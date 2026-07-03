package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode}
import dev.cmartin.aerohex.domain.port.in.{CreateAirportCommand, UpdateAirportCommand}
import sttp.tapir.Schema
import sttp.tapir.Validator

case class AirportDto(iata: String, icaoCode: String, name: String, city: String, countryCode: String)

object AirportDto {
  def fromDomain(airport: Airport): AirportDto =
    AirportDto(
      iata = airport.iataCode.value,
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

case class CreateAirportRequest(iata: String, icaoCode: String, name: String, city: String, countryCode: String)

object CreateAirportRequest {
  def toCommand(req: CreateAirportRequest): CreateAirportCommand =
    CreateAirportCommand(
      iataCode = IataCode(req.iata),
      icaoCode = req.icaoCode,
      name = req.name,
      city = req.city,
      countryCode = CountryCode(req.countryCode)
    )

  given Schema[CreateAirportRequest] = Schema.derived[CreateAirportRequest]
    .modify(_.iata)(
      _.description("3-letter IATA airport code.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .validate(Validator.pattern("[a-zA-Z]{3}"))
        .encodedExample("MAD")
    )
    .modify(_.icaoCode)(
      _.description("4-letter ICAO airport code.")
        .validate(Validator.minLength(4))
        .validate(Validator.maxLength(4))
        .validate(Validator.pattern("[a-zA-Z]{4}"))
        .encodedExample("LEMD")
    )
    .modify(_.name)(
      _.description("Full airport name.")
        .validate(Validator.minLength(1))
        .encodedExample("Adolfo Suárez Madrid-Barajas")
    )
    .modify(_.city)(
      _.description("City served by the airport.").validate(Validator.minLength(1)).encodedExample("Madrid")
    )
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .validate(Validator.pattern("[a-zA-Z]{2}"))
        .encodedExample("ES")
    )
}

case class UpdateAirportRequest(icaoCode: String, name: String, city: String, countryCode: String)

object UpdateAirportRequest {
  def toCommand(iata: String, req: UpdateAirportRequest): UpdateAirportCommand =
    UpdateAirportCommand(
      iataCode = IataCode(iata),
      icaoCode = req.icaoCode,
      name = req.name,
      city = req.city,
      countryCode = CountryCode(req.countryCode)
    )

  given Schema[UpdateAirportRequest] = Schema.derived[UpdateAirportRequest]
    .modify(_.icaoCode)(
      _.description("4-letter ICAO airport code.")
        .validate(Validator.minLength(4))
        .validate(Validator.maxLength(4))
        .validate(Validator.pattern("[a-zA-Z]{4}"))
        .encodedExample("LEMD")
    )
    .modify(_.name)(
      _.description("Full airport name.")
        .validate(Validator.minLength(1))
        .encodedExample("Adolfo Suárez Madrid-Barajas")
    )
    .modify(_.city)(
      _.description("City served by the airport.").validate(Validator.minLength(1)).encodedExample("Madrid")
    )
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .validate(Validator.pattern("[a-zA-Z]{2}"))
        .encodedExample("ES")
    )
}
