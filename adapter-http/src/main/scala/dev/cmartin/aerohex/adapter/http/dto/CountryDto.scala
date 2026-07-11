package dev.cmartin.aerohex.adapter.http.dto

import dev.cmartin.aerohex.adapter.http.CodePatterns
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.in.{CreateCountryCommand, UpdateCountryCommand}
import sttp.tapir.{Schema, Validator}

case class CountryDto(code: String, name: String)

object CountryDto {
  def fromDomain(country: Country): CountryDto =
    CountryDto(code = country.code.value, name = country.name)

  given Schema[CountryDto] = Schema.derived[CountryDto]
    .modify(_.code)(_.description("ISO 3166-1 alpha-2 country code.").encodedExample("ES"))
    .modify(_.name)(_.description("Full country name.").encodedExample("Spain"))
}

case class CreateCountryRequest(code: String, name: String)

object CreateCountryRequest {
  def toCommand(req: CreateCountryRequest): CreateCountryCommand =
    CreateCountryCommand(CountryCode(req.code), req.name)

  given Schema[CreateCountryRequest] = Schema.derived[CreateCountryRequest]
    .modify(_.code)(
      _.description("ISO 3166-1 alpha-2 country code.")
        .validate(Validator.minLength(2))
        .validate(Validator.maxLength(2))
        .validate(Validator.pattern(CodePatterns.alpha2))
        .encodedExample("ES")
    )
    .modify(_.name)(_.description("Full country name.").validate(Validator.minLength(1)).encodedExample("Spain"))
}

case class UpdateCountryRequest(name: String)

object UpdateCountryRequest {
  def toCommand(code: String, req: UpdateCountryRequest): UpdateCountryCommand =
    UpdateCountryCommand(CountryCode(code), req.name)

  given Schema[UpdateCountryRequest] = Schema.derived[UpdateCountryRequest]
    .modify(_.name)(
      _.description("Full country name.").validate(Validator.minLength(1)).encodedExample("Kingdom of Spain")
    )
}
