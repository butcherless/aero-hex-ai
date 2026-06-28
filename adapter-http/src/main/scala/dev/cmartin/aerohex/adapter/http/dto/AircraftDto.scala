package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.Aircraft
import sttp.tapir.Schema
import sttp.tapir.Validator

case class AircraftDto(registration: String, typeCode: String, airlineIcao: String)

object AircraftDto {
  def fromDomain(aircraft: Aircraft): AircraftDto =
    AircraftDto(
      registration = aircraft.registration.value,
      typeCode = aircraft.typeCode,
      airlineIcao = aircraft.airlineIcao.value
    )

  given Schema[AircraftDto] = Schema.derived[AircraftDto]
    .modify(_.registration)(_.description("International aircraft registration code.").encodedExample("EC-MIG"))
    .modify(_.typeCode)(_.description("ICAO aircraft type designator.").encodedExample("B788"))
    .modify(_.airlineIcao)(
      _.description("3-letter ICAO code of the operating airline.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
        .encodedExample("IBE")
    )
}
