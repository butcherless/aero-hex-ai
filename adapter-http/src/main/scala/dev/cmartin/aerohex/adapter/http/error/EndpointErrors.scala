package dev.cmartin.aerohex.adapter.http.error

import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object EndpointErrors:

  val unexpectedError =
    oneOfDefaultVariant(statusCode.and(jsonBody[HttpErrorResponse].description("Unexpected error.")))

  def notFoundVariant(message: String) =
    oneOfVariantValueMatcher(
      StatusCode.NotFound,
      statusCode.and(jsonBody[HttpErrorResponse].description(message))
    ) { case (s, _) => s == StatusCode.NotFound }

  def conflictVariant(message: String) =
    oneOfVariantValueMatcher(
      StatusCode.Conflict,
      statusCode.and(jsonBody[HttpErrorResponse].description(message))
    ) { case (s, _) => s == StatusCode.Conflict }

  def badRequestVariant(message: String) =
    oneOfVariantValueMatcher(
      StatusCode.BadRequest,
      statusCode.and(jsonBody[HttpErrorResponse].description(message))
    ) { case (s, _) => s == StatusCode.BadRequest }
