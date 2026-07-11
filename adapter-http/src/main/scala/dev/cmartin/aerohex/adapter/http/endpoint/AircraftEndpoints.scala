package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.{AircraftDto, CreateAircraftRequest, UpdateAircraftRequest}
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, HttpErrorResponse}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

object AircraftEndpoints {

  private val base = endpoint.in("api" / "v1" / "aircraft")

  // #6 registration marks vary in shape by country of registry (EC-MIG, N12345, G-ABCD) — no single
  // fixed pattern applies, so only non-blank + a max length are enforced, unlike IATA/ICAO path params
  private val registrationParam =
    path[String]("registration")
      .description("Aircraft registration code (e.g. EC-MIG).")
      .validate(Validator.minLength(1))
      .validate(Validator.maxLength(10))

  private val createErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.conflictVariant("Aircraft already exists."),
      EndpointErrors.notFoundVariant("Referenced airline not found."),
      EndpointErrors.unexpectedError
    )

  private val updateErrorOut: EndpointOutput[(StatusCode, HttpErrorResponse)] =
    oneOf[(StatusCode, HttpErrorResponse)](
      EndpointErrors.notFoundVariant("Aircraft not found, or referenced airline not found."),
      EndpointErrors.unexpectedError
    )

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
      .in(registrationParam)
      .out(jsonBody[AircraftDto].description("The requested aircraft."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Aircraft not found."),
          EndpointErrors.unexpectedError
        )
      )

  // #4 Location header carries the canonical URL of the created resource (HTTP best practice)
  val create: PublicEndpoint[CreateAircraftRequest, (StatusCode, HttpErrorResponse), (AircraftDto, String), Any] =
    base.post
      .summary("Create aircraft")
      .description("Creates a new aircraft.")
      .tag("Aircraft")
      .in(jsonBody[CreateAircraftRequest])
      .out(
        statusCode(StatusCode.Created)
          .and(jsonBody[AircraftDto].description("The created aircraft."))
          .and(header[String]("Location"))
      )
      .errorOut(createErrorOut)

  val update
      : PublicEndpoint[(String, UpdateAircraftRequest), (StatusCode, HttpErrorResponse), AircraftDto, Any] =
    base.put
      .summary("Update aircraft")
      .description("Updates an existing aircraft's type code and operating airline.")
      .tag("Aircraft")
      .in(registrationParam)
      .in(jsonBody[UpdateAircraftRequest])
      .out(jsonBody[AircraftDto].description("The updated aircraft."))
      .errorOut(updateErrorOut)

  val delete: PublicEndpoint[String, (StatusCode, HttpErrorResponse), Unit, Any] =
    base.delete
      .summary("Delete aircraft")
      .description("Deletes an aircraft by its registration code.")
      .tag("Aircraft")
      .in(registrationParam)
      .out(statusCode(StatusCode.NoContent))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Aircraft not found."),
          EndpointErrors.unexpectedError
        )
      )
}
