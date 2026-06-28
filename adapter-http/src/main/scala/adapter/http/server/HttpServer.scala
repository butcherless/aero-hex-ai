package adapter.http.server

import adapter.http.ApiSpec
import adapter.http.endpoint.*
import domain.port.in.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.{Response, Routes, Server}

object HttpServer {

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  private def allRoutes(
      findCountry: FindCountryUseCase,
      findAirport: FindAirportUseCase,
      findAirline: FindAirlineUseCase,
      createRoute: CreateRouteUseCase,
      findAircraft: FindAircraftUseCase,
      findFlight: FindFlightUseCase,
      findJourney: FindJourneyUseCase
  ): Routes[Any, Response] =
    CountryEndpoints.routes(findCountry) ++
      AirportEndpoints.routes(findAirport) ++
      AirlineEndpoints.routes(findAirline) ++
      RouteEndpoints.routes(createRoute) ++
      AircraftEndpoints.routes(findAircraft) ++
      FlightEndpoints.routes(findFlight) ++
      JourneyEndpoints.routes(findJourney) ++
      ZioHttpInterpreter().toHttp(
        SwaggerInterpreter(customiseDocsModel = _.tags(ApiSpec.topLevelTags))
          .fromEndpoints[Task](ApiSpec.allEndpoints, ApiSpec.info)
      )

  val serve
      : ZIO[
        FindCountryUseCase & FindAirportUseCase & FindAirlineUseCase & CreateRouteUseCase &
          FindAircraftUseCase & FindFlightUseCase & FindJourneyUseCase,
        Throwable,
        Nothing
      ] =
    for {
      findCountry  <- ZIO.service[FindCountryUseCase]
      findAirport  <- ZIO.service[FindAirportUseCase]
      findAirline  <- ZIO.service[FindAirlineUseCase]
      createRoute  <- ZIO.service[CreateRouteUseCase]
      findAircraft <- ZIO.service[FindAircraftUseCase]
      findFlight   <- ZIO.service[FindFlightUseCase]
      findJourney  <- ZIO.service[FindJourneyUseCase]
      _            <- ZIO.logInfo(s"HTTP server starting on port $port")
      result       <- Server
                        .serve(allRoutes(
                          findCountry,
                          findAirport,
                          findAirline,
                          createRoute,
                          findAircraft,
                          findFlight,
                          findJourney
                        ).handleError(identity))
                        .provide(Server.defaultWithPort(port))
    } yield result
}
