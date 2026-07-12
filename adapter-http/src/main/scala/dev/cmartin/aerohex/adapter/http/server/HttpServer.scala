package dev.cmartin.aerohex.adapter.http.server

import dev.cmartin.aerohex.adapter.http.ApiSpec
import dev.cmartin.aerohex.adapter.http.aircraft.AircraftRoutes
import dev.cmartin.aerohex.adapter.http.airline.AirlineRoutes
import dev.cmartin.aerohex.adapter.http.airport.AirportRoutes
import dev.cmartin.aerohex.adapter.http.country.CountryRoutes
import dev.cmartin.aerohex.adapter.http.flight.{FlightInstanceRoutes, FlightRoutes}
import dev.cmartin.aerohex.adapter.http.route.RouteRoutes
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.Server

object HttpServer {

  type AppRoutes =
    CountryRoutes & AirportRoutes & AirlineRoutes & RouteRoutes &
      AircraftRoutes & FlightRoutes & FlightInstanceRoutes

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  val serve: ZIO[AppRoutes, Throwable, Nothing] =
    for {
      countries       <- ZIO.service[CountryRoutes]
      airports        <- ZIO.service[AirportRoutes]
      airlines        <- ZIO.service[AirlineRoutes]
      routes          <- ZIO.service[RouteRoutes]
      aircraft        <- ZIO.service[AircraftRoutes]
      flights         <- ZIO.service[FlightRoutes]
      flightInstances <- ZIO.service[FlightInstanceRoutes]
      business         = countries.serverEndpoints ++
                           airports.serverEndpoints ++
                           airlines.serverEndpoints ++
                           routes.serverEndpoints ++
                           aircraft.serverEndpoints ++
                           flights.serverEndpoints ++
                           flightInstances.serverEndpoints
      swagger          = SwaggerInterpreter(customiseDocsModel = _.tags(ApiSpec.topLevelTags))
                           .fromServerEndpoints[Task](business, ApiSpec.info)
      _               <- ZIO.logInfo(s"HTTP server starting on port $port")
      result          <- Server
                           .serve(ZioHttpInterpreter().toHttp(business ++ swagger).handleError(identity))
                           .provide(Server.defaultWithPort(port))
    } yield result
}
