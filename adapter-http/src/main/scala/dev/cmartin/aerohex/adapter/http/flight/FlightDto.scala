package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{CreateFlightCommand, UpdateFlightCommand}
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode}
import java.time.LocalTime
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.IO

// Shared verbatim by CreateFlightRequest/UpdateFlightRequest's originIata/destinationIata/airlineIcao below.
private val originIataSchema: Schema[String] => Schema[String] = _.description(
  "IATA code of the route's origin airport."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("MAD")

private val destinationIataSchema: Schema[String] => Schema[String] = _.description(
  "IATA code of the route's destination airport."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("TFN")

private val airlineIcaoSchema: Schema[String] => Schema[String] = _.description(
  "3-letter ICAO code of the operating airline."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("AEA")

case class FlightDto(
    code: String,
    alias: Option[String],
    schedDeparture: String,
    schedArrival: String,
    originIata: String,
    destinationIata: String,
    airlineIcao: String
)

object FlightDto {
  def fromDomain(flight: Flight): FlightDto =
    FlightDto(
      code = flight.code.value,
      alias = flight.alias,
      schedDeparture = flight.schedDeparture.toString,
      schedArrival = flight.schedArrival.toString,
      originIata = flight.origin.value,
      destinationIata = flight.destination.value,
      airlineIcao = flight.airlineIcao.value
    )

  given Schema[FlightDto] = Schema.derived[FlightDto]
    .modify(_.code)(_.description("Primary airline flight code.").encodedExample("UX9117"))
    .modify(_.alias)(_.description("Alternative commercial flight code.").encodedExample("AEA9117"))
    .modify(_.schedDeparture)(_.description("Scheduled local departure time (HH:mm).").encodedExample("07:05"))
    .modify(_.schedArrival)(_.description("Scheduled local arrival time (HH:mm).").encodedExample("08:55"))
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.airlineIcao)(airlineIcaoSchema)
}

case class CreateFlightRequest(
    code: String,
    alias: Option[String],
    schedDeparture: String,
    schedArrival: String,
    originIata: String,
    destinationIata: String,
    airlineIcao: String
)

object CreateFlightRequest {
  def toCommand(req: CreateFlightRequest): IO[DomainError, CreateFlightCommand] =
    FlightCode
      .make(req.code)
      .toZIO
      .orElseFail(DomainError.InvalidFlightCode(req.code))
      .map(code =>
        CreateFlightCommand(
          code = code,
          alias = req.alias,
          schedDeparture = LocalTime.parse(req.schedDeparture),
          schedArrival = LocalTime.parse(req.schedArrival),
          origin = IataCode.unsafeMake(req.originIata),
          destination = IataCode.unsafeMake(req.destinationIata),
          airlineIcao = IcaoCode.unsafeMake(req.airlineIcao)
        )
      )

  given Schema[CreateFlightRequest] = Schema.derived[CreateFlightRequest]
    .modify(_.code)(
      _.description("Primary airline flight code.")
        .validate(Validator.minLength(1))
        .validate(Validator.maxLength(8))
        .encodedExample("UX9117")
    )
    .modify(_.alias)(_.description("Alternative commercial flight code.").encodedExample("AEA9117"))
    .modify(_.schedDeparture)(
      _.description("Scheduled local departure time (HH:mm).").encodedExample("07:05")
    )
    .modify(_.schedArrival)(_.description("Scheduled local arrival time (HH:mm).").encodedExample("08:55"))
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.airlineIcao)(airlineIcaoSchema)
}

case class UpdateFlightRequest(
    alias: Option[String],
    schedDeparture: String,
    schedArrival: String,
    originIata: String,
    destinationIata: String,
    airlineIcao: String
)

object UpdateFlightRequest {
  def toCommand(code: String, req: UpdateFlightRequest): UpdateFlightCommand =
    UpdateFlightCommand(
      code = FlightCode.unsafeMake(code),
      alias = req.alias,
      schedDeparture = LocalTime.parse(req.schedDeparture),
      schedArrival = LocalTime.parse(req.schedArrival),
      origin = IataCode.unsafeMake(req.originIata),
      destination = IataCode.unsafeMake(req.destinationIata),
      airlineIcao = IcaoCode.unsafeMake(req.airlineIcao)
    )

  given Schema[UpdateFlightRequest] = Schema.derived[UpdateFlightRequest]
    .modify(_.alias)(_.description("Alternative commercial flight code.").encodedExample("AEA9117"))
    .modify(_.schedDeparture)(
      _.description("Scheduled local departure time (HH:mm).").encodedExample("07:05")
    )
    .modify(_.schedArrival)(_.description("Scheduled local arrival time (HH:mm).").encodedExample("08:55"))
    .modify(_.originIata)(originIataSchema)
    .modify(_.destinationIata)(destinationIataSchema)
    .modify(_.airlineIcao)(airlineIcaoSchema)
}
