package dev.cmartin.aerohex.adapter.http.route

import dev.cmartin.aerohex.adapter.http.common.CodePatterns
import dev.cmartin.aerohex.adapter.http.common.PaginationParams
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object RouteEndpoints {

  private val base = endpoint.in("api" / "v1" / "routes")

  private def alphaCodeParam(name: String, description: String, length: Int, pattern: String) =
    path[String](name)
      .description(description)
      .validate(Validator.minLength(length))
      .validate(Validator.maxLength(length))
      .validate(Validator.pattern(pattern))

  private val originParam =
    alphaCodeParam("origin", "3-letter IATA code of the origin airport (e.g. MAD).", 3, CodePatterns.alpha3)

  private val destinationParam =
    alphaCodeParam(
      "destination",
      "3-letter IATA code of the destination airport (e.g. TFN).",
      3,
      CodePatterns.alpha3
    )

  private val icaoParam =
    alphaCodeParam("icao", "3-letter ICAO airline code (e.g. AEA).", 3, CodePatterns.alpha3)

  private val associationErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Route or airline not found."),
      EndpointErrors.unexpectedError
    )

  val create: PublicEndpoint[CreateRouteRequest, (StatusCode, HttpErrorResponse), RouteDto, Any] =
    base.post
      .summary("Create route")
      .description("Creates a new flight route between two airports.")
      .tag("Routes")
      .in(jsonBody[CreateRouteRequest])
      .out(jsonBody[RouteDto].description("The created route.").and(statusCode(StatusCode.Created)))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airport not found."),
          EndpointErrors.conflictVariant("A route between these airports already exists."),
          EndpointErrors.badRequestVariant("Invalid route parameters."),
          EndpointErrors.unexpectedError
        )
      )

  val associate: PublicEndpoint[(String, String, String), (StatusCode, HttpErrorResponse), Unit, Any] =
    base.post
      .summary("Associate airline with route")
      .description("Marks the given airline as operating the given route.")
      .tag("Routes")
      .in(originParam / destinationParam / "airlines" / icaoParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Route or airline not found."),
          EndpointErrors.conflictVariant("Airline is already associated with this route."),
          EndpointErrors.unexpectedError
        )
      )

  val disassociate: PublicEndpoint[(String, String, String), (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Disassociate airline from route")
      .description("Removes the given airline as an operator of the given route.")
      .tag("Routes")
      .in(originParam / destinationParam / "airlines" / icaoParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(associationErrorOut)

  val findByAirline
      : PublicEndpoint[(String, Int, Int), (StatusCode, HttpErrorResponse), List[RouteDto], Any] =
    endpoint.get
      .in("api" / "v1" / "airlines" / icaoParam / "routes")
      .summary("List routes operated by an airline")
      .description("Returns a paginated list of routes operated by the given airline.")
      .tag("Routes")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[RouteDto]].description("Routes operated by the given airline."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))
}
