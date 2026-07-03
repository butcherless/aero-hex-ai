package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{AirportDto, CreateAirportRequest}
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object AirportEndpoints {

  private val base = endpoint.in("api" / "v1" / "airports")

  // #6 same validated shape as CountryEndpoints.codeParam — kept local since that one is private
  private val countryCodeParam =
    path[String]("code")
      .description("ISO 3166-1 alpha-2 country code (e.g. ES).")
      .validate(Validator.minLength(2))
      .validate(Validator.maxLength(2))
      .validate(Validator.pattern("[a-zA-Z]{2}"))

  private val createErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Airport already exists."),
      EndpointErrors.notFoundVariant("Referenced country not found."),
      EndpointErrors.unexpectedError
    )

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AirportDto], Any] =
    base.get
      .summary("List airports")
      .description("Returns a paginated list of all airports.")
      .tag("Airports")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
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
      .in(path[String]("iata").description("3-letter IATA airport code (e.g. MAD)."))
      .out(jsonBody[AirportDto].description("The requested airport."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airport not found."),
          EndpointErrors.unexpectedError
        )
      )

  val findByCountry
      : PublicEndpoint[(String, Int, Int), (StatusCode, HttpErrorResponse), List[AirportDto], Any] =
    endpoint.get
      .in("api" / "v1" / "countries" / countryCodeParam / "airports")
      .summary("List airports in a country")
      .description("Returns a paginated list of airports belonging to the given country.")
      .tag("Airports")
      .in(query[Int]("page").description("Page number (1-based).").default(1).validate(Validator.min(1)))
      .in(
        query[Int]("pageSize")
          .description("Number of results per page (1–100).")
          .default(20)
          .validate(Validator.min(1))
          .validate(Validator.max(100))
      )
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
}
