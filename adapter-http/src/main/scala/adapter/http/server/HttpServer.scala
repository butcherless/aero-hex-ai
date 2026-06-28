package adapter.http.server

import adapter.http.ApiSpec
import adapter.http.endpoint.*
import domain.port.in.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.{Response, Routes, Server}

object HttpServer {

  type AppUseCases =
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase &
      FindAirportUseCase & FindAirlineUseCase & CreateRouteUseCase &
      FindAircraftUseCase & FindFlightUseCase & FindJourneyUseCase

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  private def routes(env: ZEnvironment[AppUseCases]): Routes[Any, Response] =
    CountryEndpoints.routes(
      env.get[FindCountryUseCase],
      env.get[CreateCountryUseCase],
      env.get[UpdateCountryUseCase],
      env.get[DeleteCountryUseCase]
    ) ++
      AirportEndpoints.routes(env.get[FindAirportUseCase]) ++
      AirlineEndpoints.routes(env.get[FindAirlineUseCase]) ++
      RouteEndpoints.routes(env.get[CreateRouteUseCase]) ++
      AircraftEndpoints.routes(env.get[FindAircraftUseCase]) ++
      FlightEndpoints.routes(env.get[FindFlightUseCase]) ++
      JourneyEndpoints.routes(env.get[FindJourneyUseCase]) ++
      ZioHttpInterpreter().toHttp(
        SwaggerInterpreter(customiseDocsModel = _.tags(ApiSpec.topLevelTags))
          .fromEndpoints[Task](ApiSpec.allEndpoints, ApiSpec.info)
      )

  val serve: ZIO[AppUseCases, Throwable, Nothing] =
    for {
      env    <- ZIO.environment[AppUseCases]
      _      <- ZIO.logInfo(s"HTTP server starting on port $port")
      result <- Server
                  .serve(routes(env).handleError(identity))
                  .provide(Server.defaultWithPort(port))
    } yield result
}
