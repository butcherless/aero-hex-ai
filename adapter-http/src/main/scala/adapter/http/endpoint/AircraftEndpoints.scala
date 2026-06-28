package adapter.http.endpoint

import adapter.http.dto.AircraftDto
import adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import domain.port.in.FindAircraftUseCase
import shared.Pagination
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import io.circe.generic.auto.*
import zio.*

object AircraftEndpoints {

  private val base = endpoint.in("api" / "v1" / "aircraft")

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AircraftDto], Any] =
    base.get
      .summary("List aircraft")
      .description("Returns a paginated list of all aircraft.")
      .tag("Aircraft")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
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

  def serverEndpoints(useCase: FindAircraftUseCase): List[ZServerEndpoint[Any, Any]] =
    List(
      findAll.zServerLogic { (page, pageSize) =>
        useCase
          .findAll(Pagination(page, pageSize))
          .map(_.map(AircraftDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
      },
      findByRegistration.zServerLogic { registration =>
        useCase
          .findByRegistration(registration)
          .map(AircraftDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
      }
    )
}
