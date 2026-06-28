package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.endpoint.*
import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.application.service.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.*
import dev.cmartin.aerohex.domain.port.out.*
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// In-memory stubs — no database or Kafka needed for API-dev mode.
// Re-wire to infrastructure layers when persistence/messaging are ready.
object WiringModule {

  private val countryRepoLayer: ULayer[CountryRepository] = ZLayer.succeed(
    new CountryRepository:
      def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Country]]          = ZIO.succeed(Nil)
      def searchByName(q: String): IO[DomainError, List[Country]]         = ZIO.succeed(Nil)
      def save(c: Country): IO[DomainError, Country]                      = ZIO.succeed(c)
      def delete(code: CountryCode): IO[DomainError, Unit]                = ZIO.unit
  )

  private val airportRepoLayer: ULayer[AirportRepository] = ZLayer.succeed(
    new AirportRepository:
      def findByIata(iata: IataCode): IO[DomainError, Option[Airport]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Airport]]       = ZIO.succeed(Nil)
      def save(a: Airport): IO[DomainError, Airport]                   = ZIO.succeed(a)
      def delete(iata: IataCode): IO[DomainError, Unit]                = ZIO.unit
  )

  private val airlineRepoLayer: ULayer[AirlineRepository] = ZLayer.succeed(
    new AirlineRepository:
      def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Airline]]       = ZIO.succeed(Nil)
      def save(a: Airline): IO[DomainError, Airline]                   = ZIO.succeed(a)
      def delete(icao: IcaoCode): IO[DomainError, Unit]                = ZIO.unit
  )

  private val routeRepoLayer: ULayer[RouteRepository] = ZLayer.succeed(
    new RouteRepository:
      def findById(id: RouteId): IO[DomainError, Option[Route]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Route]]  = ZIO.succeed(Nil)
      def save(r: Route): IO[DomainError, Route]                = ZIO.succeed(r)
      def delete(id: RouteId): IO[DomainError, Unit]            = ZIO.unit
  )

  private val aircraftRepoLayer: ULayer[AircraftRepository] = ZLayer.succeed(
    new AircraftRepository:
      def findByRegistration(reg: Registration): IO[DomainError, Option[Aircraft]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Aircraft]]                  = ZIO.succeed(Nil)
      def save(a: Aircraft): IO[DomainError, Aircraft]                             = ZIO.succeed(a)
      def delete(reg: Registration): IO[DomainError, Unit]                         = ZIO.unit
  )

  private val flightRepoLayer: ULayer[FlightRepository] = ZLayer.succeed(
    new FlightRepository:
      def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Flight]]         = ZIO.succeed(Nil)
      def save(f: Flight): IO[DomainError, Flight]                      = ZIO.succeed(f)
      def delete(code: FlightCode): IO[DomainError, Unit]               = ZIO.unit
  )

  private val journeyRepoLayer: ULayer[JourneyRepository] = ZLayer.succeed(
    new JourneyRepository:
      def findById(id: JourneyId): IO[DomainError, Option[Journey]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Journey]]    = ZIO.succeed(Nil)
      def save(j: Journey): IO[DomainError, Journey]                = ZIO.succeed(j)
      def delete(id: JourneyId): IO[DomainError, Unit]              = ZIO.unit
  )

  private val countryUseCaseLayers = (countryRepoLayer >>> FindCountryService.layer) ++
    (countryRepoLayer >>> CreateCountryService.layer) ++
    (countryRepoLayer >>> UpdateCountryService.layer) ++
    (countryRepoLayer >>> DeleteCountryService.layer)

  val appLayer: ULayer[HttpServer.AppRoutes] =
    (countryUseCaseLayers >>> CountryRoutes.layer) ++
      (airportRepoLayer >>> FindAirportService.layer >>> AirportRoutes.layer) ++
      (airlineRepoLayer >>> FindAirlineService.layer >>> AirlineRoutes.layer) ++
      ((airportRepoLayer ++ routeRepoLayer) >>> CreateRouteService.layer >>> RouteRoutes.layer) ++
      (aircraftRepoLayer >>> FindAircraftService.layer >>> AircraftRoutes.layer) ++
      (flightRepoLayer >>> FindFlightService.layer >>> FlightRoutes.layer) ++
      (journeyRepoLayer >>> FindJourneyService.layer >>> JourneyRoutes.layer)
}
