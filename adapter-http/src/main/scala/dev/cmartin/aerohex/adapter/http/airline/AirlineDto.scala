package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.CodePatterns
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.{Airline, IcaoCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.airline.{CreateAirlineCommand, UpdateAirlineCommand}
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.IO

import java.time.LocalDate

case class AirlineDto(icao: String, name: String, foundationDate: String)

object AirlineDto {
  def fromDomain(airline: Airline): AirlineDto =
    AirlineDto(
      icao = airline.icao.value,
      name = airline.name,
      foundationDate = airline.foundationDate.toString
    )

  given Schema[AirlineDto] = Schema.derived[AirlineDto]
    .modify(_.icao)(
      _.description("3-letter ICAO airline code.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
    .modify(_.name)(_.description("Full airline name.").encodedExample("Iberia"))
    .modify(_.foundationDate)(_.description("Date the airline was founded (ISO 8601).").encodedExample("1927-06-28"))
}

case class CreateAirlineRequest(icao: String, name: String, foundationDate: String, countryCode: String)

object CreateAirlineRequest {
  def toCommand(req: CreateAirlineRequest): IO[DomainError, CreateAirlineCommand] =
    IcaoCode
      .make(req.icao)
      .toZIO
      .orElseFail(DomainError.InvalidIcaoCode(req.icao))
      .map(icao =>
        CreateAirlineCommand(
          icao = icao,
          name = req.name,
          foundationDate = LocalDate.parse(req.foundationDate),
          countryCode = CountryCode.unsafeMake(req.countryCode)
        )
      )

  given Schema[CreateAirlineRequest] = Schema.derived[CreateAirlineRequest]
    .modify(_.icao)(
      _.description("3-letter ICAO airline code.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
    .modify(_.name)(
      _.description("Full airline name.").validate(Validator.minLength(1)).encodedExample("Iberia")
    )
    .modify(_.foundationDate)(
      _.description("Date the airline was founded (ISO 8601).").encodedExample("1927-06-28")
    )
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .validate(Validator.pattern(CodePatterns.alpha2))
        .encodedExample("ES")
    )
}

case class UpdateAirlineRequest(name: String, foundationDate: String, countryCode: String)

object UpdateAirlineRequest {
  def toCommand(icao: String, req: UpdateAirlineRequest): UpdateAirlineCommand =
    UpdateAirlineCommand(
      icao = IcaoCode.unsafeMake(icao),
      name = req.name,
      foundationDate = LocalDate.parse(req.foundationDate),
      countryCode = CountryCode.unsafeMake(req.countryCode)
    )

  given Schema[UpdateAirlineRequest] = Schema.derived[UpdateAirlineRequest]
    .modify(_.name)(
      _.description("Full airline name.").validate(Validator.minLength(1)).encodedExample("Iberia")
    )
    .modify(_.foundationDate)(
      _.description("Date the airline was founded (ISO 8601).").encodedExample("1927-06-28")
    )
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .validate(Validator.pattern(CodePatterns.alpha2))
        .encodedExample("ES")
    )
}
