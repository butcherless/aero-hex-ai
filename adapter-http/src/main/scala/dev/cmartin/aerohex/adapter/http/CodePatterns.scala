package dev.cmartin.aerohex.adapter.http

// Shared alphabetic-code regex patterns for Validator.pattern(...) calls across Tapir
// endpoints and DTO schemas — ISO/IATA/ICAO codes differ only in length, not shape.
object CodePatterns:
  val alpha2 = "[a-zA-Z]{2}"
  val alpha3 = "[a-zA-Z]{3}"
  val alpha4 = "[a-zA-Z]{4}"
