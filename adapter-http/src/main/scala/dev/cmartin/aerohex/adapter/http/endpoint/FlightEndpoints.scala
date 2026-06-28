package dev.cmartin.aerohex.adapter.http.endpoint

import dev.cmartin.aerohex.adapter.http.dto.FlightDto
import dev.cmartin.aerohex.adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import dev.cmartin.aerohex.domain.port.in.FindFlightUseCase
import dev.cmartin.aerohex.shared.Pagination
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import io.circe.generic.auto.*
import zio.*

object FlightEndpoints {

  private val base = endpoint.in("api" / "v1" / "flights")

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[FlightDto], Any] =
    base.get
      .summary("List flights")
      .description("Returns a paginated list of all scheduled flights.")
      .tag("Flights")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
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

  def serverEndpoints(useCase: FindFlightUseCase): List[ZServerEndpoint[Any, Any]] =
    List(
      findAll.zServerLogic { (page, pageSize) =>
        useCase
          .findAll(Pagination(page, pageSize))
          .map(_.map(FlightDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
      },
      findByCode.zServerLogic { code =>
        useCase
          .findByCode(code)
          .map(FlightDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
      }
    )
}
