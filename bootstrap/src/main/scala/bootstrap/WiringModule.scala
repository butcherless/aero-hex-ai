package bootstrap

import application.service.*
import domain.error.DomainError
import domain.model.*
import domain.port.in.*
import domain.port.out.*
import shared.Pagination
import zio.*

// In-memory stubs — no database or Kafka needed for API-dev mode.
// Re-wire to infrastructure layers when persistence/messaging are ready.
object WiringModule {

  private val countryRepoLayer: ULayer[CountryRepository] = ZLayer.succeed(
    new CountryRepository:
      def findByCode(code: CountryCode): IO[DomainError, Option[Country]] = ZIO.none
      def findAll(p: Pagination): IO[DomainError, List[Country]]          = ZIO.succeed(Nil)
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

  val appLayer: ULayer[FindCountryUseCase & FindAirportUseCase & FindAirlineUseCase & CreateRouteUseCase] =
    (countryRepoLayer >>> FindCountryService.layer) ++
      (airportRepoLayer >>> FindAirportService.layer) ++
      (airlineRepoLayer >>> FindAirlineService.layer) ++
      ((airportRepoLayer ++ routeRepoLayer) >>> CreateRouteService.layer)
}
