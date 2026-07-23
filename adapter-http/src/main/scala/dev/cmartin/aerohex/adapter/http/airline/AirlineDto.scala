package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.SchemaModifiers
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airline.{CreateAirlineCommand, UpdateAirlineCommand}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.{IO, ZIO}

// Shared verbatim by AirlineDto/CreateAirlineRequest's icao field below.
private val icaoSchema: Schema[String] => Schema[String] = _.description("3-letter ICAO airline code.")
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("IBE")

case class AirlineDto(icao: String, name: String, alias: Option[String], callsign: Option[String])

object AirlineDto {
  def fromDomain(airline: Airline): AirlineDto =
    AirlineDto(
      icao = airline.icao.value,
      name = airline.name,
      alias = airline.alias,
      callsign = airline.callsign
    )

  given Schema[AirlineDto] = Schema.derived[AirlineDto]
    .modify(_.icao)(icaoSchema)
    .modify(_.name)(_.description("Full airline name.").encodedExample("Iberia"))
    .modify(_.alias)(_.description("Alternative commercial name, if any.").encodedExample("Vueling"))
    .modify(_.callsign)(_.description("Radiotelephony callsign, if any.").encodedExample("IBERIA"))
}

case class CreateAirlineRequest(
    icao: String,
    name: String,
    alias: Option[String],
    callsign: Option[String],
    countryCode: String
)

object CreateAirlineRequest {
  def toCommand(req: CreateAirlineRequest): IO[DomainError, CreateAirlineCommand] =
    ZIO
      .fromEither(
        AirlineIcaoCode.validateAll(req.icao).toEitherWith(errs =>
          DomainError.InvalidAirlineIcaoCode(errs.toChunk.toList)
        )
      )
      .map(icao =>
        CreateAirlineCommand(
          icao = icao,
          name = req.name,
          alias = req.alias,
          callsign = req.callsign,
          countryCode = CountryCode.unsafeMake(req.countryCode)
        )
      )

  given Schema[CreateAirlineRequest] = Schema.derived[CreateAirlineRequest]
    .modify(_.icao)(icaoSchema)
    .modify(_.name)(
      _.description("Full airline name.").validate(Validator.minLength(1)).encodedExample("Iberia")
    )
    .modify(_.alias)(_.description("Alternative commercial name, if any.").encodedExample("Vueling"))
    .modify(_.callsign)(_.description("Radiotelephony callsign, if any.").encodedExample("IBERIA"))
    .modify(_.countryCode)(SchemaModifiers.countryCode)
}

case class UpdateAirlineRequest(name: String, alias: Option[String], callsign: Option[String], countryCode: String)

object UpdateAirlineRequest {
  def toCommand(icao: String, req: UpdateAirlineRequest): UpdateAirlineCommand =
    UpdateAirlineCommand(
      icao = AirlineIcaoCode.unsafeMake(icao),
      name = req.name,
      alias = req.alias,
      callsign = req.callsign,
      countryCode = CountryCode.unsafeMake(req.countryCode)
    )

  given Schema[UpdateAirlineRequest] = Schema.derived[UpdateAirlineRequest]
    .modify(_.name)(
      _.description("Full airline name.").validate(Validator.minLength(1)).encodedExample("Iberia")
    )
    .modify(_.alias)(_.description("Alternative commercial name, if any.").encodedExample("Vueling"))
    .modify(_.callsign)(_.description("Radiotelephony callsign, if any.").encodedExample("IBERIA"))
    .modify(_.countryCode)(SchemaModifiers.countryCode)
}
