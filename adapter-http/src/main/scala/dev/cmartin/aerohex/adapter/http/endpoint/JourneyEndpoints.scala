package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.JourneyDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object JourneyEndpoints {

  private val base = endpoint.in("api" / "v1" / "journeys")

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[JourneyDto], Any] =
    base.get
      .summary("List journeys")
      .description("Returns a paginated list of all journeys.")
      .tag("Journeys")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
      .out(jsonBody[List[JourneyDto]].description("List of journeys."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findById: PublicEndpoint[String, (StatusCode, HttpErrorResponse), JourneyDto, Any] =
    base.get
      .summary("Find journey by ID")
      .description("Returns a single journey identified by its UUID.")
      .tag("Journeys")
      .in(path[String]("id").description("Journey UUID."))
      .out(jsonBody[JourneyDto].description("The requested journey."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Journey not found."),
          EndpointErrors.unexpectedError
        )
      )
}
