package adapter.http.endpoint

import adapter.http.dto.AirlineDto
import adapter.http.error.{ErrorMapper, HttpErrorResponse}
import domain.port.in.FindAirlineUseCase
import shared.Pagination
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.RichZEndpoint
import io.circe.generic.auto.*
import zio.*
import zio.http.{Response, Routes}

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
      .errorOut(statusCode.and(jsonBody[HttpErrorResponse].description("An error occurred.")))

  val findByIcao: PublicEndpoint[String, (StatusCode, HttpErrorResponse), AirlineDto, Any] =
    base.get
      .summary("Find airline by ICAO code")
      .description("Returns a single airline identified by its 3-letter ICAO code.")
      .tag("Airlines")
      .in(path[String]("icao").description("3-letter ICAO airline code (e.g. IBE)."))
      .out(jsonBody[AirlineDto].description("The requested airline."))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          oneOfVariantValueMatcher(
            StatusCode.NotFound,
            statusCode.and(jsonBody[HttpErrorResponse].description("Airline not found."))
          ) { case (s, _) => s == StatusCode.NotFound },
          oneOfDefaultVariant(statusCode.and(jsonBody[HttpErrorResponse].description("Unexpected error.")))
        )
      )

  def routes(useCase: FindAirlineUseCase): Routes[Any, Response] =
    ZioHttpInterpreter().toHttp(
      findAll.zServerLogic { input =>
        val (page, pageSize) = input
        useCase
          .findAll(Pagination(page, pageSize))
          .map(_.map(AirlineDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
      }
    ) ++
      ZioHttpInterpreter().toHttp(
        findByIcao.zServerLogic { icao =>
          useCase
            .findByIcao(icao)
            .map(AirlineDto.fromDomain)
            .mapError(ErrorMapper.toHttpError)
        }
      )
}
