package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.domain.model.Airline
import sttp.tapir.Schema
import sttp.tapir.Validator

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
