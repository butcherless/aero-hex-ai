package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.AircraftDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object AircraftEndpoints {

  private val base = endpoint.in("api" / "v1" / "aircraft")

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AircraftDto], Any] =
    base.get
      .summary("List aircraft")
      .description("Returns a paginated list of all aircraft.")
      .tag("Aircraft")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[AircraftDto]].description("List of aircraft."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findByRegistration: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AircraftDto, Any] =
    base.get
      .summary("Find aircraft by registration")
      .description("Returns a single aircraft identified by its international registration code.")
      .tag("Aircraft")
      .in(path[String]("registration").description("Aircraft registration code (e.g. EC-MIG)."))
      .out(jsonBody[AircraftDto].description("The requested aircraft."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Aircraft not found."),
          EndpointErrors.unexpectedError
        )
      )
}
