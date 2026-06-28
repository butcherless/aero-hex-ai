package adapter.http.endpoint

import adapter.http.dto.{CreateRouteRequest, RouteDto}
import adapter.http.error.{EndpointErrors, ErrorMapper, HttpErrorResponse}
import domain.port.in.{CreateRouteCommand, CreateRouteUseCase}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.RichZEndpoint
import io.circe.generic.auto.*
import zio.*
import zio.http.{Response, Routes}

object RouteEndpoints {

  private val base = endpoint.in("api" / "v1" / "routes")

  val create: PublicEndpoint[CreateRouteRequest, (StatusCode, HttpErrorResponse), RouteDto, Any] =
    base.post
      .summary("Create route")
      .description("Creates a new flight route between two airports operated by a given airline.")
      .tag("Routes")
      .in(jsonBody[CreateRouteRequest])
      .out(jsonBody[RouteDto].description("The created route.").and(statusCode(StatusCode.Created)))
      .errorOut(
        oneOf[(StatusCode, HttpErrorResponse)](
          EndpointErrors.notFoundVariant("Airport or airline not found."),
          EndpointErrors.conflictVariant("A route between these airports for this airline already exists."),
          EndpointErrors.badRequestVariant("Invalid route parameters."),
          EndpointErrors.unexpectedError
        )
      )

  def routes(useCase: CreateRouteUseCase): Routes[Any, Response] =
    ZioHttpInterpreter().toHttp(
      create.zServerLogic { req =>
        useCase
          .create(CreateRouteCommand(req.originIata, req.destinationIata, req.airlineIcao, req.distanceKm))
          .map(RouteDto.fromDomain)
          .mapError(ErrorMapper.toHttpError)
      }
    )
}
