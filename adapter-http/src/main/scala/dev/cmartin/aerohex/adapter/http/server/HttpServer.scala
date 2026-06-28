package dev.cmartin.aerohex.adapter.http.server

import dev.cmartin.aerohex.adapter.http.ApiSpec
import dev.cmartin.aerohex.adapter.http.endpoint.*
import dev.cmartin.aerohex.domain.port.in.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*
import zio.http.Server

object HttpServer {

  type AppUseCases =
    FindCountryUseCase & CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase &
      FindAirportUseCase & FindAirlineUseCase & CreateRouteUseCase &
      FindAircraftUseCase & FindFlightUseCase & FindJourneyUseCase

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  // #1/#2 Aggregates all resource server endpoints; HttpServer owns the single toHttp call
  private def businessEndpoints(env: ZEnvironment[AppUseCases]): List[ZServerEndpoint[Any, Any]] =
    CountryEndpoints.serverEndpoints(
      env.get[FindCountryUseCase],
      env.get[CreateCountryUseCase],
      env.get[UpdateCountryUseCase],
      env.get[DeleteCountryUseCase]
    ) ++
      AirportEndpoints.serverEndpoints(env.get[FindAirportUseCase]) ++
      AirlineEndpoints.serverEndpoints(env.get[FindAirlineUseCase]) ++
      RouteEndpoints.serverEndpoints(env.get[CreateRouteUseCase]) ++
      AircraftEndpoints.serverEndpoints(env.get[FindAircraftUseCase]) ++
      FlightEndpoints.serverEndpoints(env.get[FindFlightUseCase]) ++
      JourneyEndpoints.serverEndpoints(env.get[FindJourneyUseCase])

  val serve: ZIO[AppUseCases, Throwable, Nothing] =
    for {
      env     <- ZIO.environment[AppUseCases]
      business = businessEndpoints(env)
      // #3 Swagger endpoints derived from server endpoints — no separate allEndpoints list needed here
      swagger  = SwaggerInterpreter(customiseDocsModel = _.tags(ApiSpec.topLevelTags))
                   .fromServerEndpoints[Task](business, ApiSpec.info)
      _       <- ZIO.logInfo(s"HTTP server starting on port $port")
      result  <- Server
                   .serve(ZioHttpInterpreter().toHttp(business ++ swagger).handleError(identity))
                   .provide(Server.defaultWithPort(port))
    } yield result
}
