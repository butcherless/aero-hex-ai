package dev.cmartin.aerohex.adapter.http.server

import dev.cmartin.aerohex.adapter.http.ApiSpec
import dev.cmartin.aerohex.adapter.http.endpoint.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.Server

object HttpServer {

  type AppRoutes =
    CountryRoutes & AirportRoutes & AirlineRoutes & RouteRoutes &
      AircraftRoutes & FlightRoutes & JourneyRoutes

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  val serve: ZIO[AppRoutes, Throwable, Nothing] =
    for {
      countries <- ZIO.service[CountryRoutes]
      airports  <- ZIO.service[AirportRoutes]
      airlines  <- ZIO.service[AirlineRoutes]
      routes    <- ZIO.service[RouteRoutes]
      aircraft  <- ZIO.service[AircraftRoutes]
      flights   <- ZIO.service[FlightRoutes]
      journeys  <- ZIO.service[JourneyRoutes]
      business   = countries.serverEndpoints ++
                     airports.serverEndpoints ++
                     airlines.serverEndpoints ++
                     routes.serverEndpoints ++
                     aircraft.serverEndpoints ++
                     flights.serverEndpoints ++
                     journeys.serverEndpoints
      swagger    = SwaggerInterpreter(customiseDocsModel = _.tags(ApiSpec.topLevelTags))
                     .fromServerEndpoints[Task](business, ApiSpec.info)
      _         <- ZIO.logInfo(s"HTTP server starting on port $port")
      result    <- Server
                     .serve(ZioHttpInterpreter().toHttp(business ++ swagger).handleError(identity))
                     .provide(Server.defaultWithPort(port))
    } yield result
}
