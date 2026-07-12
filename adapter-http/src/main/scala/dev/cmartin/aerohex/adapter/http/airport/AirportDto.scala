package dev.cmartin.aerohex.adapter.http.airport

import dev.cmartin.aerohex.adapter.http.common.{CodePatterns, SchemaModifiers}
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.{Airport, IataCode}
import dev.cmartin.aerohex.domain.airport.{CreateAirportCommand, UpdateAirportCommand}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.IO

case class AirportDto(iata: String, icaoCode: String, name: String, city: String)

object AirportDto {
  def fromDomain(airport: Airport): AirportDto =
    AirportDto(
      iata = airport.iataCode.value,
      icaoCode = airport.icaoCode.value,
      name = airport.name,
      city = airport.city
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
}

case class CreateAirportRequest(iata: String, icaoCode: String, name: String, city: String, countryCode: String)

object CreateAirportRequest {
  def toCommand(req: CreateAirportRequest): IO[DomainError, CreateAirportCommand] =
    for
      iataCode <- IataCode.make(req.iata).toZIO.orElseFail(DomainError.InvalidIataCode(req.iata))
      icaoCode <- IcaoCode.make(req.icaoCode).toZIO.orElseFail(DomainError.InvalidIcaoCode(req.icaoCode))
    yield CreateAirportCommand(
      iataCode = iataCode,
      icaoCode = icaoCode,
      name = req.name,
      city = req.city,
      countryCode = CountryCode.unsafeMake(req.countryCode)
    )

  given Schema[CreateAirportRequest] = Schema.derived[CreateAirportRequest]
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
    .modify(_.name)(
      _.description("Full airport name.")
        .validate(Validator.minLength(1))
        .encodedExample("Adolfo Suárez Madrid-Barajas")
    )
    .modify(_.city)(
      _.description("City served by the airport.").validate(Validator.minLength(1)).encodedExample("Madrid")
    )
    .modify(_.countryCode)(SchemaModifiers.countryCode)
}

case class UpdateAirportRequest(icaoCode: String, name: String, city: String, countryCode: String)

object UpdateAirportRequest {
  def toCommand(iata: String, req: UpdateAirportRequest): UpdateAirportCommand =
    UpdateAirportCommand(
      iataCode = IataCode.unsafeMake(iata),
      icaoCode = IcaoCode.unsafeMake(req.icaoCode),
      name = req.name,
      city = req.city,
      countryCode = CountryCode.unsafeMake(req.countryCode)
    )

  given Schema[UpdateAirportRequest] = Schema.derived[UpdateAirportRequest]
    .modify(_.icaoCode)(
      _.description("4-letter ICAO airport code.")
        .validate(Validator.minLength(4))
        .validate(Validator.maxLength(4))
        .validate(Validator.pattern(CodePatterns.alpha4))
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
    .modify(_.countryCode)(SchemaModifiers.countryCode)
}
