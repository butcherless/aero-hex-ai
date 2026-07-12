package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object RouteEndpoints {

  private val base = endpoint.in("api" / "v1" / "routes")

  val create: PublicEndpoint[CreateRouteRequest, (StatusCode, HttpErrorResponse), RouteDto, Any] =
    base.post
      .summary("Create route")
      .description("Creates a new flight route between two airports operated by a given airline.")
      .tag("Routes")
      .in(jsonBody[CreateRouteRequest])
      .out(jsonBody[RouteDto].description("The created route.").and(statusCode(StatusCode.Created)))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airport or airline not found."),
          EndpointErrors.conflictVariant("A route between these airports for this airline already exists."),
          EndpointErrors.badRequestVariant("Invalid route parameters."),
          EndpointErrors.unexpectedError
        )
      )
}
