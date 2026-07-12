package dev.cmartin.aerohex.adapter.http.airline

import dev.cmartin.aerohex.adapter.http.common.CodePatterns
import dev.cmartin.aerohex.adapter.http.common.PaginationParams
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object AirlineEndpoints {

  private val base = endpoint.in("api" / "v1" / "airlines")

  // Same 4-validator shape needed by every alpha code path param in this file (and, per the
  // AirportEndpoints/CountryEndpoints equivalents, in theirs too — kept local here rather than
  // shared across files since each of those is private to its own endpoint object).
  private def alphaCodeParam(name: String, description: String, length: Int, pattern: String) =
    path[String](name)
      .description(description)
      .validate(Validator.minLength(length))
      .validate(Validator.maxLength(length))
      .validate(Validator.pattern(pattern))

  private val icaoParam =
    alphaCodeParam("icao", "3-letter ICAO airline code (e.g. IBE).", 3, CodePatterns.alpha3)

  private val countryCodeParam =
    alphaCodeParam("code", "ISO 3166-1 alpha-2 country code (e.g. ES).", 2, CodePatterns.alpha2)

  private val notFoundErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Airline not found."),
      EndpointErrors.unexpectedError
    )

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
      .errorOut(notFoundErrorOut)

  val findByCountry
      : PublicEndpoint[(String, Int, Int), (StatusCode, HttpErrorResponse), List[AirlineDto], Any] =
    endpoint.get
      .in("api" / "v1" / "countries" / countryCodeParam / "airlines")
      .summary("List airlines in a country")
      .description("Returns a paginated list of airlines registered in the given country.")
      .tag("Airlines")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[AirlineDto]].description("Airlines registered in the given country."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Country not found."),
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
      .errorOut(notFoundErrorOut)
}
