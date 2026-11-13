package dev.cmartin.aerohex.adapter.http.common

import sttp.tapir.*

// Shared by every endpoint with an ISO/IATA/ICAO code in its path. These codes differ only in
// length, so one validated shape covers them all: exactly `length` ASCII letters, rejected with
// 400 before the handler runs.
object PathParams:
  def alphaCode(name: String, description: String, length: Int): EndpointInput.PathCapture[String] =
    path[String](name)
      .description(description)
      .validate(Validator.minLength(length))
      .validate(Validator.maxLength(length))
      .validate(Validator.pattern(s"[a-zA-Z]{$length}"))
