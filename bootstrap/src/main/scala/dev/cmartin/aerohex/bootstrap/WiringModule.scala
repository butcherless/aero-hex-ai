package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.endpoint.*
import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.application.service.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.*
import dev.cmartin.aerohex.domain.port.out.*
import dev.cmartin.aerohex.infrastructure.persistence.postgres.config.PostgresConfig
import dev.cmartin.aerohex.infrastructure.persistence.postgres.repository.DoobieAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.repository.QuillCountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// CountryRepository is wired to Postgres via Quill (POC); AirportRepository is wired to
// Postgres via Doobie. All other repositories use in-memory stubs.
object WiringModule {

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val airportRepoLayer: TaskLayer[AirportRepository] =
    PostgresConfig.transactorLayer >>> DoobieAirportRepository.layer

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

  private val airportUseCaseLayers = (airportRepoLayer >>> FindAirportService.layer) ++
    (airportRepoLayer >>> CreateAirportService.layer) ++
    (airportRepoLayer >>> UpdateAirportService.layer) ++
    ((airportRepoLayer ++ countryRepoLayer) >>> FindAirportsByCountryService.layer)

  val appLayer: TaskLayer[HttpServer.AppRoutes] =
    (countryUseCaseLayers >>> CountryRoutes.layer) ++
      (airportUseCaseLayers >>> AirportRoutes.layer) ++
      (airlineRepoLayer >>> FindAirlineService.layer >>> AirlineRoutes.layer) ++
      ((airportRepoLayer ++ routeRepoLayer) >>> CreateRouteService.layer >>> RouteRoutes.layer) ++
      (aircraftRepoLayer >>> FindAircraftService.layer >>> AircraftRoutes.layer) ++
      (flightRepoLayer >>> FindFlightService.layer >>> FlightRoutes.layer) ++
      (journeyRepoLayer >>> FindJourneyService.layer >>> JourneyRoutes.layer)
}
