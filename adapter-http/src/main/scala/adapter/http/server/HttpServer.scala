package adapter.http.server

import adapter.http.endpoint.*
import domain.port.in.*
import sttp.apispec.Tag as ApiTag
import sttp.apispec.openapi.{Contact, Info, License}
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.{Response, Routes, Server}

object HttpServer {

  val port: Int = sys.env.get("HTTP_PORT").flatMap(_.toIntOption).getOrElse(8080)

  private val apiInfo = Info(
    title = "Aviation Hexagonal API",
    version = "0.1.0",
    description = Some("REST API for managing countries, airports, airlines, routes, aircraft, flights, and journeys."),
    contact = Some(Contact(name = Some("Aviation API Team"), email = Some("api@aviation.example"))),
    license = Some(License(name = "Apache 2.0", url = Some("https://www.apache.org/licenses/LICENSE-2.0")))
  )

  private val topLevelTags = List(
    ApiTag("Countries", description = Some("Country lookup operations.")),
    ApiTag("Airports", description = Some("Airport lookup operations.")),
    ApiTag("Airlines", description = Some("Airline lookup operations.")),
    ApiTag("Routes", description = Some("Flight route management operations.")),
    ApiTag("Aircraft", description = Some("Aircraft lookup operations.")),
    ApiTag("Flights", description = Some("Scheduled flight lookup operations.")),
    ApiTag("Journeys", description = Some("Journey (actual flight instance) lookup operations."))
  )

  private val allEndpoints = List(
    CountryEndpoints.findAll,
    CountryEndpoints.findByCode,
    AirportEndpoints.findAll,
    AirportEndpoints.findByIata,
    AirlineEndpoints.findAll,
    AirlineEndpoints.findByIcao,
    RouteEndpoints.create,
    AircraftEndpoints.findAll,
    AircraftEndpoints.findByRegistration,
    FlightEndpoints.findAll,
    FlightEndpoints.findByCode,
    JourneyEndpoints.findAll,
    JourneyEndpoints.findById
  )

  def allRoutes(
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
        SwaggerInterpreter(customiseDocsModel = _.tags(topLevelTags))
          .fromEndpoints[Task](allEndpoints, apiInfo)
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
