package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.CodePatterns
import dev.cmartin.aerohex.adapter.http.dto.AirlineDto
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
}
