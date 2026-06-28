package adapter.http.endpoint

import adapter.http.dto.JourneyDto
import adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import domain.port.in.FindJourneyUseCase
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
      .errorOut(statusCode.and(jsonBody[HttpErrorResponse].description("An error occurred.")))

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

  def routes(useCase: FindJourneyUseCase): Routes[Any, Response] =
    ZioHttpInterpreter().toHttp(
      findAll.zServerLogic { input =>
        val (page, pageSize) = input
        useCase
          .findAll(Pagination(page, pageSize))
          .map(_.map(JourneyDto.fromDomain))
          .mapError(ErrorMapper.toHttpError)
      }
    ) ++
      ZioHttpInterpreter().toHttp(
        findById.zServerLogic { id =>
          useCase
            .findById(id)
            .map(JourneyDto.fromDomain)
            .mapError(ErrorMapper.toHttpError)
        }
      )
}
