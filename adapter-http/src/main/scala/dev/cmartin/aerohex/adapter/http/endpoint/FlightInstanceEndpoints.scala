package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.FlightInstanceDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object FlightInstanceEndpoints {

  private val base = endpoint.in("api" / "v1" / "flight-instances")

  private val idParam =
    path[String]("id")
      .description("Flight instance UUID.")
      .validate(Validator.pattern("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[FlightInstanceDto], Any] =
    base.get
      .summary("List flight instances")
      .description("Returns a paginated list of all flight instances.")
      .tag("Flight Instances")
      .in(PaginationParams.page)
      .in(PaginationParams.pageSize)
      .out(jsonBody[List[FlightInstanceDto]].description("List of flight instances."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findById: PublicEndpoint[String, (StatusCode, HttpErrorResponse), FlightInstanceDto, Any] =
    base.get
      .summary("Find flight instance by ID")
      .description("Returns a single flight instance identified by its UUID.")
      .tag("Flight Instances")
      .in(idParam)
      .out(jsonBody[FlightInstanceDto].description("The requested flight instance."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Flight instance not found."),
          EndpointErrors.unexpectedError
        )
      )
}
