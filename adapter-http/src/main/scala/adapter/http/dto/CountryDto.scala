package adapter.http.dto

import domain.model.Country
import domain.port.in.{CreateCountryCommand, UpdateCountryCommand}
import sttp.tapir.Schema
import sttp.tapir.Validator

case class CountryDto(code: String, name: String)

object CountryDto {
  def fromDomain(country: Country): CountryDto =
    CountryDto(code = country.code.value, name = country.name)

  given Schema[CountryDto] = Schema.derived[CountryDto]
    .modify(_.code)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .encodedExample("ES")
    )
    .modify(_.name)(_.description("Full country name.").encodedExample("Spain"))
}

case class CreateCountryRequest(code: String, name: String)

object CreateCountryRequest {
  def toCommand(req: CreateCountryRequest): CreateCountryCommand =
    CreateCountryCommand(req.code, req.name)

  given Schema[CreateCountryRequest] = Schema.derived[CreateCountryRequest]
    .modify(_.code)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .encodedExample("ES")
    )
    .modify(_.name)(_.description("Full country name.").validate(Validator.minLength(1)).encodedExample("Spain"))
}

case class UpdateCountryRequest(name: String)

object UpdateCountryRequest {
  def toCommand(code: String, req: UpdateCountryRequest): UpdateCountryCommand =
    UpdateCountryCommand(code, req.name)

  given Schema[UpdateCountryRequest] = Schema.derived[UpdateCountryRequest]
    .modify(_.name)(
      _.description("Full country name.").validate(Validator.minLength(1)).encodedExample("Kingdom of Spain")
    )
}
