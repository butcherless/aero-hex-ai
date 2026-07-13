package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{Route, RouteAirlineRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

private[application] object RouteAirlineRepositoryStub:

  val unimplementedRouteAirlineRepo: RouteAirlineRepository = new RouteAirlineRepository:
    def associate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit]    =
      ZIO.die(new NotImplementedError("associate"))
    def disassociate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit] =
      ZIO.die(new NotImplementedError("disassociate"))
    def findAirlines(o: IataCode, d: IataCode): IO[DomainError, List[Airline]]               =
      ZIO.die(new NotImplementedError("findAirlines"))
    def findRoutes(icao: AirlineIcaoCode, p: Pagination): IO[DomainError, List[Route]]       =
      ZIO.die(new NotImplementedError("findRoutes"))

  def stubRouteAirlineRepo(
      onAssociate: (IataCode, IataCode, AirlineIcaoCode) => IO[DomainError, Unit] =
        unimplementedRouteAirlineRepo.associate,
      onDisassociate: (IataCode, IataCode, AirlineIcaoCode) => IO[DomainError, Unit] =
        unimplementedRouteAirlineRepo.disassociate,
      onFindAirlines: (IataCode, IataCode) => IO[DomainError, List[Airline]] =
        unimplementedRouteAirlineRepo.findAirlines,
      onFindRoutes: (AirlineIcaoCode, Pagination) => IO[DomainError, List[Route]] =
        unimplementedRouteAirlineRepo.findRoutes
  ): RouteAirlineRepository = new RouteAirlineRepository:
    def associate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit]    = onAssociate(o, d, icao)
    def disassociate(o: IataCode, d: IataCode, icao: AirlineIcaoCode): IO[DomainError, Unit] =
      onDisassociate(o, d, icao)
    def findAirlines(o: IataCode, d: IataCode): IO[DomainError, List[Airline]]               = onFindAirlines(o, d)
    def findRoutes(icao: AirlineIcaoCode, p: Pagination): IO[DomainError, List[Route]]       = onFindRoutes(icao, p)
