package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.FlightDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object FlightEndpoints {

  private val base = endpoint.in("api" / "v1" / "flights")

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
      .in(path[String]("code").description("Airline flight code (e.g. UX9117)."))
      .out(jsonBody[FlightDto].description("The requested flight."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Flight not found."),
          EndpointErrors.unexpectedError
        )
      )
}
