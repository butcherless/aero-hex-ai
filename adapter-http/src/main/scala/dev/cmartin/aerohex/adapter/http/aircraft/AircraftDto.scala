package dev.cmartin.aerohex.adapter.http.aircraft

import dev.cmartin.aerohex.domain.aircraft.{Aircraft, Registration}
import dev.cmartin.aerohex.domain.aircraft.{CreateAircraftCommand, UpdateAircraftCommand}
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import sttp.tapir.Schema
import sttp.tapir.Validator
import zio.IO

// Shared verbatim by AircraftDto/CreateAircraftRequest/UpdateAircraftRequest's airlineIcao field below.
private val airlineIcaoSchema: Schema[String] => Schema[String] = _.description(
  "3-letter ICAO code of the operating airline."
)
  .validate(Validator.minLength(3))
  .validate(Validator.maxLength(3))
  .encodedExample("IBE")

case class AircraftDto(registration: String, typeCode: String, description: String, airlineIcao: String)

object AircraftDto {
  def fromDomain(aircraft: Aircraft): AircraftDto =
    AircraftDto(
      registration = aircraft.registration.value,
      typeCode = aircraft.typeCode,
      description = aircraft.description,
      airlineIcao = aircraft.airlineIcao.value
    )

  given Schema[AircraftDto] = Schema.derived[AircraftDto]
    .modify(_.registration)(_.description("International aircraft registration code.").encodedExample("EC-MIG"))
    .modify(_.typeCode)(_.description("ICAO aircraft type designator.").encodedExample("B788"))
    .modify(_.description)(
      _.description("Common/marketing name of the aircraft type.").encodedExample("Airbus A330-900")
    )
    .modify(_.airlineIcao)(airlineIcaoSchema)
}

case class CreateAircraftRequest(registration: String, typeCode: String, description: String, airlineIcao: String)

object CreateAircraftRequest {
  def toCommand(req: CreateAircraftRequest): IO[DomainError, CreateAircraftCommand] =
    Registration
      .make(req.registration)
      .toZIO
      .orElseFail(DomainError.InvalidRegistration(req.registration))
      .map(registration =>
        CreateAircraftCommand(
          registration = registration,
          typeCode = req.typeCode,
          description = req.description,
          airlineIcao = IcaoCode.unsafeMake(req.airlineIcao)
        )
      )

  given Schema[CreateAircraftRequest] = Schema.derived[CreateAircraftRequest]
    .modify(_.registration)(
      _.description("International aircraft registration code.")
        .validate(Validator.minLength(1))
        .validate(Validator.maxLength(10))
        .encodedExample("EC-MIG")
    )
    .modify(_.typeCode)(
      _.description("ICAO aircraft type designator.").validate(Validator.minLength(1)).encodedExample("B788")
    )
    .modify(_.description)(
      _.description("Common/marketing name of the aircraft type.")
        .validate(Validator.minLength(1))
        .encodedExample("Airbus A330-900")
    )
    .modify(_.airlineIcao)(airlineIcaoSchema)
}

case class UpdateAircraftRequest(typeCode: String, description: String, airlineIcao: String)

object UpdateAircraftRequest {
  def toCommand(registration: String, req: UpdateAircraftRequest): UpdateAircraftCommand =
    UpdateAircraftCommand(
      registration = Registration.unsafeMake(registration),
      typeCode = req.typeCode,
      description = req.description,
      airlineIcao = IcaoCode.unsafeMake(req.airlineIcao)
    )

  given Schema[UpdateAircraftRequest] = Schema.derived[UpdateAircraftRequest]
    .modify(_.typeCode)(
      _.description("ICAO aircraft type designator.").validate(Validator.minLength(1)).encodedExample("B788")
    )
    .modify(_.description)(
      _.description("Common/marketing name of the aircraft type.")
        .validate(Validator.minLength(1))
        .encodedExample("Airbus A330-900")
    )
    .modify(_.airlineIcao)(airlineIcaoSchema)
}
