package dev.cmartin.aerohex.adapter.http.airport

import dev.cmartin.aerohex.adapter.http.common.CodePatterns
import dev.cmartin.aerohex.adapter.http.common.PaginationParams
import dev.cmartin.aerohex.adapter.http.country.CountryDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object AirportEndpoints {

  private val base = endpoint.in("api" / "v1" / "airports")

  // #6 same validated shape as CountryEndpoints.codeParam — kept local since that one is private
  private val countryCodeParam =
    path[String]("code")
      .description("ISO 3166-1 alpha-2 country code (e.g. ES).")
      .validate(Validator.minLength(2))
      .validate(Validator.maxLength(2))
      .validate(Validator.pattern(CodePatterns.alpha2))

  // #6 shared validated path param — findByIata previously captured "iata" with no validation at all
  private val iataParam =
    path[String]("iata")
      .description("3-letter IATA airport code (e.g. MAD).")
      .validate(Validator.minLength(3))
      .validate(Validator.maxLength(3))
      .validate(Validator.pattern(CodePatterns.alpha3))

  // Same shape as findByIata's/delete's errorOut below — extracted since a third occurrence
  // (findCountry) would otherwise triplicate it.
  private val notFoundErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Airport not found."),
      EndpointErrors.unexpectedError
    )

  private val createErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Airport already exists."),
      EndpointErrors.notFoundVariant("Referenced country not found."),
      EndpointErrors.badRequestVariant("Invalid IATA or ICAO code."),
      EndpointErrors.unexpectedError
    )

  private val updateErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Airport not found, or referenced country not found."),
      EndpointErrors.unexpectedError
    )

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AirportDto], Any] =
    base.get
      .summary("List airports")
      .description("Returns a paginated list of all airports.")
      .tag("Airports")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[AirportDto]].description("List of airports."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val searchByName: PublicEndpoint[String, (StatusCode, HttpErrorResponse), List[AirportDto], Any] =
    base.get
      .summary("Search airports by name")
      .description(
        "Returns all airports whose name contains the given query string (case-insensitive). Query must be at least 3 characters."
      )
      .tag("Airports")
      .in("search")
      .in(
        query[String]("q")
          .description("Name fragment to search for (minimum 3 characters).")
          .validate(Validator.minLength(3))
          .example("Madrid")
      )
      .out(jsonBody[List[AirportDto]].description("Matching airports."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.badRequestVariant("Invalid search query."),
          EndpointErrors.unexpectedError
        )
      )

  val findByIata: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AirportDto, Any] =
    base.get
      .summary("Find airport by IATA code")
      .description("Returns a single airport identified by its 3-letter IATA code.")
      .tag("Airports")
      .in(iataParam)
      .out(jsonBody[AirportDto].description("The requested airport."))
      .errorOut(notFoundErrorOut)

  val findCountry: PublicEndpoint[String, (StatusCode, HttpErrorResponse), CountryDto, Any] =
    base.get
      .summary("Find the country an airport belongs to")
      .description("Returns the country of the airport identified by its 3-letter IATA code.")
      .tag("Airports")
      .in(iataParam)
      .in("country")
      .out(jsonBody[CountryDto].description("The airport's country."))
      .errorOut(notFoundErrorOut)

  val findByCountry
      : PublicEndpoint[(String, Int, Int), (StatusCode, HttpErrorResponse), List[AirportDto], Any] =
    endpoint.get
      .in("api" / "v1" / "countries" / countryCodeParam / "airports")
      .summary("List airports in a country")
      .description("Returns a paginated list of airports belonging to the given country.")
      .tag("Airports")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[AirportDto]].description("Airports in the given country."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Country not found."),
          EndpointErrors.unexpectedError
        )
      )

  // #4 Location header carries the canonical URL of the created resource (HTTP best practice)
  val create: PublicEndpoint[CreateAirportRequest, (StatusCode, HttpErrorResponse), (AirportDto, String), Any] =
    base.post
      .summary("Create airport")
      .description("Creates a new airport.")
      .tag("Airports")
      .in(jsonBody[CreateAirportRequest])
      .out(
        statusCode(StatusCode.Created)
          .and(jsonBody[AirportDto].description("The created airport."))
          .and(header[String]("Location"))
      )
      .errorOut(createErrorOut)

  val update: PublicEndpoint[(String, UpdateAirportRequest), (StatusCode, HttpErrorResponse), AirportDto, Any] =
    base.put
      .summary("Update airport")
      .description("Updates an existing airport's ICAO code, name, city, and country.")
      .tag("Airports")
      .in(iataParam)
      .in(jsonBody[UpdateAirportRequest])
      .out(jsonBody[AirportDto].description("The updated airport."))
      .errorOut(updateErrorOut)

  val delete: PublicEndpoint[String, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Delete airport")
      .description("Deletes an airport by its 3-letter IATA code.")
      .tag("Airports")
      .in(iataParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundErrorOut)
}
