package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.{Aircraft, IcaoCode, Registration}
import dev.cmartin.aerohex.domain.port.in.{CreateAircraftCommand, UpdateAircraftCommand}
import sttp.tapir.Schema
import sttp.tapir.Validator

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
    .modify(_.airlineIcao)(
      _.description("3-letter ICAO code of the operating airline.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
}

case class CreateAircraftRequest(registration: String, typeCode: String, description: String, airlineIcao: String)

object CreateAircraftRequest {
  def toCommand(req: CreateAircraftRequest): CreateAircraftCommand =
    CreateAircraftCommand(
      registration = Registration(req.registration),
      typeCode = req.typeCode,
      description = req.description,
      airlineIcao = IcaoCode(req.airlineIcao)
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
    .modify(_.airlineIcao)(
      _.description("3-letter ICAO code of the operating airline.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
}

case class UpdateAircraftRequest(typeCode: String, description: String, airlineIcao: String)

object UpdateAircraftRequest {
  def toCommand(registration: String, req: UpdateAircraftRequest): UpdateAircraftCommand =
    UpdateAircraftCommand(
      registration = Registration(registration),
      typeCode = req.typeCode,
      description = req.description,
      airlineIcao = IcaoCode(req.airlineIcao)
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
    .modify(_.airlineIcao)(
      _.description("3-letter ICAO code of the operating airline.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
}
