package dev.cmartin.aerohex.adapter.http.common

import sttp.tapir.{Schema, Validator}

// Shared Tapir Schema field modifiers for value shapes reused verbatim across resources'
// create/update request DTOs (e.g. a countryCode reference field on both Airline and Airport).
object SchemaModifiers:
  val countryCode: Schema[String] => Schema[String] = _.description("ISO 3166-1 alpha-2 country code.")
    .validate(Validator.minLength(2))
    .validate(Validator.maxLength(2))
    .validate(Validator.pattern(CodePatterns.alpha2))
    .encodedExample("ES")
