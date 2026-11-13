package dev.cmartin.aerohex.adapter.http.error

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object EndpointErrors:

  val unexpectedError =
    oneOfDefaultVariant(statusCode.and(jsonBody[HttpErrorResponse].description("Unexpected error.")))

  // One documented error variant: selected when the handler fails with `status`, and listed in
  // the OpenAPI spec with `message` as the response description.
  private def variant(status: StatusCode, message: String) =
    oneOfVariantValueMatcher(
      status,
      statusCode.and(jsonBody[HttpErrorResponse].description(message))
    ) { case (s, _) => s == status }

  def notFoundVariant(message: String)     = variant(StatusCode.NotFound, message)
  def conflictVariant(message: String)     = variant(StatusCode.Conflict, message)
  def badRequestVariant(message: String)   = variant(StatusCode.BadRequest, message)
  def unauthorizedVariant(message: String) = variant(StatusCode.Unauthorized, message)
