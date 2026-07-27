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
import dev.cmartin.aerohex.domain.aircraft.*
import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.*
import dev.cmartin.aerohex.domain.route.*
import dev.cmartin.aerohex.domain.user.{
  LoginUseCase,
  LogoutUseCase,
  PasswordHasher,
  RevokedTokenRepository,
  TokenService,
  UserRepository
}
import dev.cmartin.aerohex.infrastructure.persistence.quill.aircraft.QuillAircraftRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.flight.QuillFlightRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.user.{QuillRevokedTokenRepository, QuillUserRepository}
import dev.cmartin.aerohex.infrastructure.security.{BcryptPasswordHasher, JwtConfig, JwtService}
import dev.cmartin.aerohex.shared.Pagination
import zio.*

// POLICY: every real-persistence repository must be wired to the SAME implementation (currently
// Quill, sharing one QuillDataSourceLayer.live DataSource/pool) — no mixing implementations
// across entities. If this ever switches to a different persistence library, switch every wired
// repository in the same change, not one at a time, to avoid the split Country=Quill/Airport=X
// state this project went through with its former Doobie implementation.
// Route/RouteAirline/FlightInstance repositories use in-memory stubs.
object WiringModule {

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val airportRepoLayer: TaskLayer[AirportRepository] =
    QuillDataSourceLayer.live >>> QuillAirportRepository.layer

  private val airlineRepoLayer: TaskLayer[AirlineRepository] =
    QuillDataSourceLayer.live >>> QuillAirlineRepository.layer

  private val aircraftRepoLayer: TaskLayer[AircraftRepository] =
    QuillDataSourceLayer.live >>> QuillAircraftRepository.layer

  private val userRepoLayer: TaskLayer[UserRepository] =
    QuillDataSourceLayer.live >>> QuillUserRepository.layer

  private val revokedTokenRepoLayer: TaskLayer[RevokedTokenRepository] =
    QuillDataSourceLayer.live >>> QuillRevokedTokenRepository.layer

  private val jwtServiceLayer: TaskLayer[TokenService] =
    (ZLayer.succeed(JwtConfig.default) ++ revokedTokenRepoLayer) >>> JwtService.layer

  private val passwordHasherLayer: ULayer[PasswordHasher] = BcryptPasswordHasher.layer

  private val routeRepoLayer: ULayer[RouteRepository] = ZLayer.succeed(
    new RouteRepository:
      def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[Route]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Route]]                    = ZIO.succeed(Nil)
      def save(r: Route): IO[DomainError, Route]                                  = ZIO.succeed(r)
      def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                 = ZIO.unit
  )

  private val routeAirlineRepoLayer: ULayer[RouteAirlineRepository] = ZLayer.succeed(
    new RouteAirlineRepository:
      def associate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit]    = ZIO.unit
      def disassociate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit] = ZIO.unit
      def findAirlines(o: IataCode, d: IataCode): IO[DomainError, List[Airline]]               = ZIO.succeed(Nil)
      def findRoutes(icao: AirlineIcaoCode, p: Pagination): IO[DomainError, List[Route]]       = ZIO.succeed(Nil)
  )

  private val flightRepoLayer: TaskLayer[FlightRepository] =
    QuillDataSourceLayer.live >>> QuillFlightRepository.layer

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

  private val airportUseCaseLayers =
    (airportRepoLayer >>> FindAirportService.layer) ++
      (airportRepoLayer >>> CreateAirportService.layer) ++
      (airportRepoLayer >>> UpdateAirportService.layer) ++
      (airportRepoLayer >>> DeleteAirportService.layer) ++
      ((airportRepoLayer ++ countryRepoLayer) >>> FindAirportsByCountryService.layer) ++
      (airportRepoLayer >>> FindCountryForAirportService.layer)

  private val airlineUseCaseLayers =
    (airlineRepoLayer >>> FindAirlineService.layer) ++
      (airlineRepoLayer >>> CreateAirlineService.layer) ++
      (airlineRepoLayer >>> UpdateAirlineService.layer) ++
      (airlineRepoLayer >>> DeleteAirlineService.layer) ++
      ((airlineRepoLayer ++ countryRepoLayer) >>> FindAirlinesByCountryService.layer) ++
      (routeAirlineRepoLayer >>> FindAirlinesByRouteService.layer)

  private val aircraftUseCaseLayers = (aircraftRepoLayer >>> FindAircraftService.layer) ++
    (aircraftRepoLayer >>> CreateAircraftService.layer) ++
    (aircraftRepoLayer >>> UpdateAircraftService.layer) ++
    (aircraftRepoLayer >>> DeleteAircraftService.layer)

  private val authUseCaseLayers: TaskLayer[LoginUseCase] =
    (userRepoLayer ++ passwordHasherLayer ++ jwtServiceLayer) >>> LoginService.layer

  private val logoutUseCaseLayer: TaskLayer[LogoutUseCase] =
    jwtServiceLayer >>> LogoutService.layer

  private val routeUseCaseLayers =
    (((airportRepoLayer >>> FindAirportService.layer) ++ routeRepoLayer) >>> CreateRouteService.layer) ++
      (routeAirlineRepoLayer >>> AssociateAirlineService.layer) ++
      (routeAirlineRepoLayer >>> DisassociateAirlineService.layer) ++
      (routeAirlineRepoLayer >>> FindRoutesByAirlineService.layer)

  private val flightUseCaseLayers =
    (flightRepoLayer >>> FindFlightService.layer) ++
      (flightRepoLayer >>> CreateFlightService.layer) ++
      (flightRepoLayer >>> UpdateFlightService.layer) ++
      (flightRepoLayer >>> DeleteFlightService.layer) ++
      (flightRepoLayer >>> FindFlightsByAirlineService.layer) ++
      (flightRepoLayer >>> FindAirlineForFlightService.layer)

  // Every business resource's XxxRoutes now also needs TokenService (step 2 of the security
  // rollout, plans/security/protect-endpoints.md) — merged in here rather than threaded through
  // each *UseCaseLayers val, since it's an HTTP-layer concern (SecuredEndpoint), not a use-case
  // dependency. AuthRoutes also needs it directly (login already required it for issuance;
  // logout's own SecuredEndpoint wrapper needs it too, step 3 — plans/security/logout.md).
  // Health stays fully unprotected, so jwtServiceLayer isn't merged into HealthRoutes.layer.
  val appLayer: TaskLayer[HttpServer.AppRoutes] =
    ((countryUseCaseLayers ++ jwtServiceLayer) >>> CountryRoutes.layer) ++
      ((airportUseCaseLayers ++ jwtServiceLayer) >>> AirportRoutes.layer) ++
      ((airlineUseCaseLayers ++ jwtServiceLayer) >>> AirlineRoutes.layer) ++
      ((routeUseCaseLayers ++ jwtServiceLayer) >>> RouteRoutes.layer) ++
      ((aircraftUseCaseLayers ++ jwtServiceLayer) >>> AircraftRoutes.layer) ++
      ((flightUseCaseLayers ++ jwtServiceLayer) >>> FlightRoutes.layer) ++
      (((flightInstanceRepoLayer >>> FindFlightInstanceService.layer) ++ jwtServiceLayer) >>>
        FlightInstanceRoutes.layer) ++
      ((authUseCaseLayers ++ logoutUseCaseLayer ++ jwtServiceLayer) >>> AuthRoutes.layer) ++
      (QuillDataSourceLayer.live >>> HealthRoutes.layer)
}
