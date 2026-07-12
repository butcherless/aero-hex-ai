package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.aircraft.AircraftRoutes
import dev.cmartin.aerohex.adapter.http.airline.AirlineRoutes
import dev.cmartin.aerohex.adapter.http.airport.AirportRoutes
import dev.cmartin.aerohex.adapter.http.country.CountryRoutes
import dev.cmartin.aerohex.adapter.http.flight.{FlightInstanceRoutes, FlightRoutes}
import dev.cmartin.aerohex.adapter.http.route.RouteRoutes
import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.application.aircraft.*
import dev.cmartin.aerohex.application.airline.*
import dev.cmartin.aerohex.application.airport.*
import dev.cmartin.aerohex.application.country.*
import dev.cmartin.aerohex.application.flight.*
import dev.cmartin.aerohex.application.route.*
import dev.cmartin.aerohex.domain.aircraft.*
import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.*
import dev.cmartin.aerohex.domain.route.*
import dev.cmartin.aerohex.infrastructure.persistence.quill.aircraft.QuillAircraftRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// POLICY: every real-persistence repository must be wired to the SAME implementation (currently
// Quill, sharing one QuillDataSourceLayer.live DataSource/pool) — no mixing Quill and Doobie
// across entities. If this ever switches (e.g. back to Doobie), switch every wired repository in
// the same change, not one at a time, to avoid the split Country=Quill/Airport=Doobie state this
// project went through. Doobie implementations (DoobieCountryRepository, DoobieAirportRepository,
// DoobieAirlineRepository, DoobieAircraftRepository, DoobieRouteRepository) still exist in
// persistence-postgres and are kept schema-consistent, but none are wired here today. All other
// repositories use in-memory stubs.
object WiringModule {

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val airportRepoLayer: TaskLayer[AirportRepository] =
    QuillDataSourceLayer.live >>> QuillAirportRepository.layer

  private val airlineRepoLayer: TaskLayer[AirlineRepository] =
    QuillDataSourceLayer.live >>> QuillAirlineRepository.layer

  private val aircraftRepoLayer: TaskLayer[AircraftRepository] =
    QuillDataSourceLayer.live >>> QuillAircraftRepository.layer

  private val routeRepoLayer: ULayer[RouteRepository] = ZLayer.succeed(
    new RouteRepository:
      def findById(id: RouteId): IO[DomainError, Option[Route]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Route]]  = ZIO.succeed(Nil)
      def save(r: Route): IO[DomainError, Route]                = ZIO.succeed(r)
      def delete(id: RouteId): IO[DomainError, Unit]            = ZIO.unit
  )

  private val flightRepoLayer: ULayer[FlightRepository] = ZLayer.succeed(
    new FlightRepository:
      def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Flight]]         = ZIO.succeed(Nil)
      def save(f: Flight): IO[DomainError, Flight]                      = ZIO.succeed(f)
      def delete(code: FlightCode): IO[DomainError, Unit]               = ZIO.unit
  )

  private val flightInstanceRepoLayer: ULayer[FlightInstanceRepository] = ZLayer.succeed(
    new FlightInstanceRepository:
      def findById(id: FlightInstanceId): IO[DomainError, Option[FlightInstance]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[FlightInstance]]           = ZIO.succeed(Nil)
      def save(j: FlightInstance): IO[DomainError, FlightInstance]                = ZIO.succeed(j)
      def delete(id: FlightInstanceId): IO[DomainError, Unit]                     = ZIO.unit
  )

  private val countryUseCaseLayers = (countryRepoLayer >>> FindCountryService.layer) ++
    (countryRepoLayer >>> CreateCountryService.layer) ++
    (countryRepoLayer >>> UpdateCountryService.layer) ++
    (countryRepoLayer >>> DeleteCountryService.layer)

  private val airportUseCaseLayers = (airportRepoLayer >>> FindAirportService.layer) ++
    (airportRepoLayer >>> CreateAirportService.layer) ++
    (airportRepoLayer >>> UpdateAirportService.layer) ++
    (airportRepoLayer >>> DeleteAirportService.layer) ++
    ((airportRepoLayer ++ countryRepoLayer) >>> FindAirportsByCountryService.layer)

  private val airlineUseCaseLayers = (airlineRepoLayer >>> FindAirlineService.layer) ++
    (airlineRepoLayer >>> CreateAirlineService.layer) ++
    (airlineRepoLayer >>> UpdateAirlineService.layer) ++
    (airlineRepoLayer >>> DeleteAirlineService.layer)

  private val aircraftUseCaseLayers = (aircraftRepoLayer >>> FindAircraftService.layer) ++
    (aircraftRepoLayer >>> CreateAircraftService.layer) ++
    (aircraftRepoLayer >>> UpdateAircraftService.layer) ++
    (aircraftRepoLayer >>> DeleteAircraftService.layer)

  val appLayer: TaskLayer[HttpServer.AppRoutes] =
    (countryUseCaseLayers >>> CountryRoutes.layer) ++
      (airportUseCaseLayers >>> AirportRoutes.layer) ++
      (airlineUseCaseLayers >>> AirlineRoutes.layer) ++
      (((airportRepoLayer >>> FindAirportService.layer) ++ routeRepoLayer) >>> CreateRouteService.layer >>>
        RouteRoutes.layer) ++
      (aircraftUseCaseLayers >>> AircraftRoutes.layer) ++
      (flightRepoLayer >>> FindFlightService.layer >>> FlightRoutes.layer) ++
      (flightInstanceRepoLayer >>> FindFlightInstanceService.layer >>> FlightInstanceRoutes.layer)
}
