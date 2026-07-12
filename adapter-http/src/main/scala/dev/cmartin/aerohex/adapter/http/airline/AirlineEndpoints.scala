package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.PaginationParams
import dev.cmartin.aerohex.adapter.http.common.CodePatterns
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object AirlineEndpoints {

  private val base = endpoint.in("api" / "v1" / "airlines")

  // same validated shape as AirportEndpoints.iataParam / CountryEndpoints.codeParam
  private val icaoParam =
    path[String]("icao")
      .description("3-letter ICAO airline code (e.g. IBE).")
      .validate(Validator.minLength(3))
      .validate(Validator.maxLength(3))
      .validate(Validator.pattern(CodePatterns.alpha3))

  private val createErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Airline already exists."),
      EndpointErrors.notFoundVariant("Referenced country not found."),
      EndpointErrors.badRequestVariant("Invalid ICAO code."),
      EndpointErrors.unexpectedError
    )

  private val updateErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Airline not found, or referenced country not found."),
      EndpointErrors.unexpectedError
    )

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AirlineDto], Any] =
    base.get
      .summary("List airlines")
      .description("Returns a paginated list of all airlines.")
      .tag("Airlines")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[AirlineDto]].description("List of airlines."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findByIcao: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AirlineDto, Any] =
    base.get
      .summary("Find airline by ICAO code")
      .description("Returns a single airline identified by its 3-letter ICAO code.")
      .tag("Airlines")
      .in(icaoParam)
      .out(jsonBody[AirlineDto].description("The requested airline."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airline not found."),
          EndpointErrors.unexpectedError
        )
      )

  // #4 Location header carries the canonical URL of the created resource (HTTP best practice)
  val create: PublicEndpoint[CreateAirlineRequest, (StatusCode, HttpErrorResponse), (AirlineDto, String), Any] =
    base.post
      .summary("Create airline")
      .description("Creates a new airline.")
      .tag("Airlines")
      .in(jsonBody[CreateAirlineRequest])
      .out(
        statusCode(StatusCode.Created)
          .and(jsonBody[AirlineDto].description("The created airline."))
          .and(header[String]("Location"))
      )
      .errorOut(createErrorOut)

  val update: PublicEndpoint[(String, UpdateAirlineRequest), (StatusCode, HttpErrorResponse), AirlineDto, Any] =
    base.put
      .summary("Update airline")
      .description("Updates an existing airline's name, foundation date, and country.")
      .tag("Airlines")
      .in(icaoParam)
      .in(jsonBody[UpdateAirlineRequest])
      .out(jsonBody[AirlineDto].description("The updated airline."))
      .errorOut(updateErrorOut)

  val delete: PublicEndpoint[String, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Delete airline")
      .description("Deletes an airline by its 3-letter ICAO code.")
      .tag("Airlines")
      .in(icaoParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airline not found."),
          EndpointErrors.unexpectedError
        )
      )
}
