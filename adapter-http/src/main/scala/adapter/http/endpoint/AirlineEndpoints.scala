package adapter.http.endpoint

import adapter.http.dto.AirlineDto
import adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import domain.port.in.FindAirlineUseCase
import shared.Pagination
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint}
import io.circe.generic.auto.*
import zio.*

object AirlineEndpoints {

  private val base = endpoint.in("api" / "v1" / "airlines")

  val findAll: PublicEndpoint[(Int, Int), (StatusCode, HttpErrorResponse), List[AirlineDto], Any] =
    base.get
      .summary("List airlines")
      .description("Returns a paginated list of all airlines.")
      .tag("Airlines")
      .in(query[Int]("page").description("Page number (1-based).").default(1))
      .in(query[Int]("pageSize").description("Number of results per page.").default(20))
      .out(jsonBody[List[AirlineDto]].description("List of airlines."))
      .errorOut(oneOf[(StatusCode, HttpErrorResponse)](EndpointErrors.unexpectedError))

  val findByIcao: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AirlineDto, Any] =
    base.get
      .summary("Find airline by ICAO code")
      .description("Returns a single airline identified by its 3-letter ICAO code.")
      .tag("Airlines")
      .in(path[String]("icao").description("3-letter ICAO airline code (e.g. IBE)."))
      .out(jsonBody[AirlineDto].description("The requested airline."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airline not found."),
          EndpointErrors.unexpectedError
        )
      )

  def serverEndpoints(useCase: FindAirlineUseCase): List[ZServerEndpoint[Any, Any]] =
    List(
      findAll.zServerLogic { (page, pageSize) =>
        useCase
          .findAll(Pagination(page, pageSize))
          .map(_.map(AirlineDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
      },
      findByIcao.zServerLogic { icao =>
        useCase
          .findByIcao(icao)
          .map(AirlineDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
      }
    )
}
