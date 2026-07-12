package dev.cmartin.aerohex.adapter.http.country

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.country.{CreateCountryCommand, UpdateCountryCommand}
import sttp.tapir.{Schema, Validator}
import zio.IO

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

  // BR-01 (shape) enforced by CountryCode's own Newtype assertion, not a Tapir Validator here —
  // see domain/model/Country.scala. minLength/maxLength stay for OpenAPI-visible bounds; the alpha
  // check moves to the domain layer, the single source of truth for "what is a country code".
  def toCommand(req: CreateCountryRequest): IO[DomainError, CreateCountryCommand] =
    CountryCode.make(req.code).toZIO
      .orElseFail(DomainError.InvalidCountryCode(req.code))
      .map(CreateCountryCommand(_, req.name))

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
    UpdateCountryCommand(CountryCode.unsafeMake(code), req.name)

  given Schema[UpdateCountryRequest] = Schema.derived[UpdateCountryRequest]
    .modify(_.name)(
      _.description("Full country name.").validate(Validator.minLength(1)).encodedExample("Kingdom of Spain")
    )
}
