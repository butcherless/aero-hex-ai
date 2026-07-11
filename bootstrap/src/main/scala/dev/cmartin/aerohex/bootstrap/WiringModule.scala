package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.endpoint.*
import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.application.service.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.*
import dev.cmartin.aerohex.domain.port.out.*
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.repository.{
  QuillAirlineRepository,
  QuillAirportRepository,
  QuillCountryRepository
}
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// POLICY: every real-persistence repository must be wired to the SAME implementation (currently
// Quill, sharing one QuillDataSourceLayer.live DataSource/pool) — no mixing Quill and Doobie
// across entities. If this ever switches (e.g. back to Doobie), switch every wired repository in
// the same change, not one at a time, to avoid the split Country=Quill/Airport=Doobie state this
// project went through. Doobie implementations (DoobieCountryRepository, DoobieAirportRepository,
// DoobieAirlineRepository, DoobieRouteRepository) still exist in persistence-postgres and are kept
// schema-consistent, but none are wired here today. All other repositories use in-memory stubs.
object WiringModule {

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val airportRepoLayer: TaskLayer[AirportRepository] =
    QuillDataSourceLayer.live >>> QuillAirportRepository.layer

  private val airlineRepoLayer: TaskLayer[AirlineRepository] =
    QuillDataSourceLayer.live >>> QuillAirlineRepository.layer

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

  val appLayer: TaskLayer[HttpServer.AppRoutes] =
    (countryUseCaseLayers >>> CountryRoutes.layer) ++
      (airportUseCaseLayers >>> AirportRoutes.layer) ++
      (airlineUseCaseLayers >>> AirlineRoutes.layer) ++
      (((airportRepoLayer >>> FindAirportService.layer) ++ routeRepoLayer) >>> CreateRouteService.layer >>>
        RouteRoutes.layer) ++
      (aircraftRepoLayer >>> FindAircraftService.layer >>> AircraftRoutes.layer) ++
      (flightRepoLayer >>> FindFlightService.layer >>> FlightRoutes.layer) ++
      (flightInstanceRepoLayer >>> FindFlightInstanceService.layer >>> FlightInstanceRoutes.layer)
}
