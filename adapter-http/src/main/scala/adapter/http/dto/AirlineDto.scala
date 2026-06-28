package adapter.http.dto

import domain.model.Airline
import sttp.tapir.Schema
import sttp.tapir.Validator

case class AirlineDto(icao: String, name: String, foundationDate: String, countryCode: String)

object AirlineDto {
  def fromDomain(airline: Airline): AirlineDto =
    AirlineDto(
      icao = airline.icao.value,
      name = airline.name,
      foundationDate = airline.foundationDate.toString,
      countryCode = airline.countryCode.value
    )

  given Schema[AirlineDto] = Schema.derived[AirlineDto]
    .modify(_.icao)(
      _.description("3-letter ICAO airline code.")
        .validate(Validator.minLength(3))
        .validate(Validator.maxLength(3))
    )
    .modify(_.name)(_.description("Full airline name."))
    .modify(_.foundationDate)(_.description("Date the airline was founded (ISO 8601, e.g. 1927-06-28)."))
    .modify(_.countryCode)(
      _.description("ISO 3166-1 alpha-2 country code of the airline's home country.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
    )
}
