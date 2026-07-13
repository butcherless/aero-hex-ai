package dev.cmartin.aerohex.adapter.http.flight

import dev.cmartin.aerohex.adapter.http.airline.AirlineDto
import dev.cmartin.aerohex.adapter.http.common.{CodePatterns, PaginationParams}
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import io.circe.generic.auto.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*

object FlightEndpoints {

  private val base = endpoint.in("api" / "v1" / "flights")

  // #6 flight codes vary in shape across codeshares/charters (e.g. "UX9117", "AA1") — no single
  // fixed pattern applies, so only non-blank + a max length are enforced, unlike IATA/ICAO path params
  private val codeParam =
    path[String]("code")
      .description("Airline flight code (e.g. UX9117).")
      .validate(Validator.minLength(1))
      .validate(Validator.maxLength(8))

  private val icaoParam =
    path[String]("icao")
      .description("3-letter ICAO airline code (e.g. IBE).")
      .validate(Validator.minLength(3))
      .validate(Validator.maxLength(3))
      .validate(Validator.pattern(CodePatterns.alpha3))

  // Same shape as findByCode's/delete's errorOut below — extracted since a third occurrence
  // (findAirline) would otherwise triplicate it.
  private val notFoundErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Flight not found."),
      EndpointErrors.unexpectedError
    )

  private val createErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Flight already exists."),
      EndpointErrors.notFoundVariant("Referenced airport or airline not found."),
      EndpointErrors.badRequestVariant("Invalid flight code."),
      EndpointErrors.unexpectedError
    )

  private val updateErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Flight not found, or referenced airport/airline not found."),
      EndpointErrors.unexpectedError
    )

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[FlightDto], Any] =
    base.get
      .summary("List flights")
      .description("Returns a paginated list of all scheduled flights.")
      .tag("Flights")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[FlightDto]].description("List of flights."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findByCode: PublicEndpoint[String, (StatusCode, HttpErrorResponse), FlightDto, Any] =
    base.get
      .summary("Find flight by code")
      .description("Returns a single scheduled flight identified by its airline flight code.")
      .tag("Flights")
      .in(codeParam)
      .out(jsonBody[FlightDto].description("The requested flight."))
      .errorOut(notFoundErrorOut)

  val findAirline: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AirlineDto, Any] =
    base.get
      .summary("Find the airline operating a flight")
      .description("Returns the airline operating the flight identified by its airline flight code.")
      .tag("Flights")
      .in(codeParam)
      .in("airline")
      .out(jsonBody[AirlineDto].description("The flight's operating airline."))
      .errorOut(notFoundErrorOut)

  val findByAirline
      : PublicEndpoint[(String, Int, Int), (StatusCode, HttpErrorResponse), List[FlightDto], Any] =
    endpoint.get
      .in("api" / "v1" / "airlines" / icaoParam / "flights")
      .summary("List flights operated by an airline")
      .description("Returns a paginated list of scheduled flights operated by the given airline.")
      .tag("Flights")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[FlightDto]].description("Flights operated by the given airline."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  // #4 Location header carries the canonical URL of the created resource (HTTP best practice)
  val create: PublicEndpoint[CreateFlightRequest, (StatusCode, HttpErrorResponse), (FlightDto, String), Any] =
    base.post
      .summary("Create flight")
      .description("Creates a new scheduled flight.")
      .tag("Flights")
      .in(jsonBody[CreateFlightRequest])
      .out(
        statusCode(StatusCode.Created)
          .and(jsonBody[FlightDto].description("The created flight."))
          .and(header[String]("Location"))
      )
      .errorOut(createErrorOut)

  val update: PublicEndpoint[(String, UpdateFlightRequest), (StatusCode, HttpErrorResponse), FlightDto, Any] =
    base.put
      .summary("Update flight")
      .description("Updates an existing flight's schedule, route, and operating airline.")
      .tag("Flights")
      .in(codeParam)
      .in(jsonBody[UpdateFlightRequest])
      .out(jsonBody[FlightDto].description("The updated flight."))
      .errorOut(updateErrorOut)

  val delete: PublicEndpoint[String, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Delete flight")
      .description("Deletes a flight by its airline flight code.")
      .tag("Flights")
      .in(codeParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(notFoundErrorOut)
}
