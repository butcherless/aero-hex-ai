package dev.cmartin.aerohex.bootstrap

import dev.cmartin.aerohex.adapter.http.aircraft.AircraftRoutes
import dev.cmartin.aerohex.adapter.http.airline.AirlineRoutes
import dev.cmartin.aerohex.adapter.http.airport.AirportRoutes
import dev.cmartin.aerohex.adapter.http.auth.AuthRoutes
import dev.cmartin.aerohex.adapter.http.country.CountryRoutes
import dev.cmartin.aerohex.adapter.http.flight.{FlightInstanceRoutes, FlightRoutes}
import dev.cmartin.aerohex.adapter.http.health.HealthRoutes
import dev.cmartin.aerohex.adapter.http.route.RouteRoutes
import dev.cmartin.aerohex.adapter.http.server.HttpServer
import dev.cmartin.aerohex.application.aircraft.*
import dev.cmartin.aerohex.application.airline.*
import dev.cmartin.aerohex.application.airport.*
import dev.cmartin.aerohex.application.auth.{LoginService, LogoutService}
import dev.cmartin.aerohex.application.country.*
import dev.cmartin.aerohex.application.flight.*
import dev.cmartin.aerohex.application.route.*
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{FlightInstance, FlightInstanceId, FlightInstanceRepository}
import dev.cmartin.aerohex.domain.route.{Route, RouteAirlineRepository}
import dev.cmartin.aerohex.infrastructure.persistence.quill.aircraft.QuillAircraftRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.flight.QuillFlightRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.route.QuillRouteRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.user.{QuillRevokedTokenRepository, QuillUserRepository}
import dev.cmartin.aerohex.infrastructure.security.{BcryptPasswordHasher, JwtConfig, JwtService}
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// POLICY: every real-persistence repository must be wired to the SAME implementation (currently
// Quill, sharing one QuillDataSourceLayer.live DataSource/pool) — no mixing implementations
// across entities. If this ever switches to a different persistence library, switch every wired
// repository in the same change, not one at a time, to avoid the split Country=Quill/Airport=X
// state this project went through with its former Doobie implementation.
// RouteAirline/FlightInstance repositories use in-memory stubs.
object WiringModule {

  private val routeAirlineRepoStub: ULayer[RouteAirlineRepository] = ZLayer.succeed(
    new RouteAirlineRepository:
      def associate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit]    = ZIO.unit
      def disassociate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit] = ZIO.unit
      def findAirlines(o: IataCode, d: IataCode): IO[DomainError, List[Airline]]               = ZIO.succeed(Nil)
      def findRoutes(icao: AirlineIcaoCode, p: Pagination): IO[DomainError, List[Route]]       = ZIO.succeed(Nil)
  )

  private val flightInstanceRepoStub: ULayer[FlightInstanceRepository] = ZLayer.succeed(
    new FlightInstanceRepository:
      def findById(id: FlightInstanceId): IO[DomainError, Option[FlightInstance]] = ZIO.none
      def findAll(p: Pagination): UIO[List[FlightInstance]]                       = ZIO.succeed(Nil)
      def save(j: FlightInstance): IO[DomainError, FlightInstance]                = ZIO.succeed(j)
      def delete(id: FlightInstanceId): IO[DomainError, Unit]                     = ZIO.unit
  )

  // ZLayer.make builds the dependency graph at compile time: a missing or duplicate provider is a
  // compile error, and every layer, including the DataSource pool and the TokenService, is built
  // exactly once and shared by everything that needs it. List order doesn't matter.
  val appLayer: TaskLayer[HttpServer.AppRoutes] =
    ZLayer.make[HttpServer.AppRoutes](
      // Infrastructure
      QuillDataSourceLayer.live,
      QuillCountryRepository.layer,
      QuillAirportRepository.layer,
      QuillAirlineRepository.layer,
      QuillAircraftRepository.layer,
      QuillRouteRepository.layer,
      QuillFlightRepository.layer,
      QuillUserRepository.layer,
      QuillRevokedTokenRepository.layer,
      routeAirlineRepoStub,
      flightInstanceRepoStub,
      ZLayer.fromZIO(JwtConfig.fromEnv()),
      JwtService.layer,
      BcryptPasswordHasher.layer,
      // Use cases
      FindCountryService.layer,
      CreateCountryService.layer,
      UpdateCountryService.layer,
      DeleteCountryService.layer,
      FindAirportService.layer,
      CreateAirportService.layer,
      UpdateAirportService.layer,
      DeleteAirportService.layer,
      FindAirportsByCountryService.layer,
      FindCountryForAirportService.layer,
      FindAirlineService.layer,
      CreateAirlineService.layer,
      UpdateAirlineService.layer,
      DeleteAirlineService.layer,
      FindAirlinesByCountryService.layer,
      FindAirlinesByRouteService.layer,
      FindAircraftService.layer,
      CreateAircraftService.layer,
      UpdateAircraftService.layer,
      DeleteAircraftService.layer,
      FindAircraftByAirlineService.layer,
      CreateRouteService.layer,
      AssociateAirlineService.layer,
      DisassociateAirlineService.layer,
      FindRoutesByAirlineService.layer,
      FindRouteService.layer,
      FindFlightService.layer,
      CreateFlightService.layer,
      UpdateFlightService.layer,
      DeleteFlightService.layer,
      FindFlightsByAirlineService.layer,
      FindAirlineForFlightService.layer,
      FindFlightInstanceService.layer,
      LoginService.layer,
      LogoutService.layer,
      // HTTP adapters
      CountryRoutes.layer,
      AirportRoutes.layer,
      AirlineRoutes.layer,
      RouteRoutes.layer,
      AircraftRoutes.layer,
      FlightRoutes.layer,
      FlightInstanceRoutes.layer,
      AuthRoutes.layer,
      HealthRoutes.layer
    )
}
