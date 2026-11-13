package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{Route, RouteRepository, RouteWithAirportNames}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, UIO, ZIO}

// Repository stub where every method dies unless overridden — see AirlineRepositoryStub for the
// rationale (a call a test doesn't expect should surface loudly, not silently return a default).
private[application] object RouteRepositoryStub:

  val unimplementedRouteRepo: RouteRepository = new RouteRepository:
    def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[RouteWithAirportNames]]     =
      ZIO.die(new NotImplementedError("findBySegment"))
    def findAll(p: Pagination): UIO[List[RouteWithAirportNames]]                                    =
      ZIO.die(new NotImplementedError("findAll"))
    def findAllUnbounded: UIO[List[Route]]                                                          =
      ZIO.die(new NotImplementedError("findAllUnbounded"))
    def findByOrigin(o: IataCode, p: Pagination): IO[DomainError, List[RouteWithAirportNames]]      =
      ZIO.die(new NotImplementedError("findByOrigin"))
    def findByDestination(d: IataCode, p: Pagination): IO[DomainError, List[RouteWithAirportNames]] =
      ZIO.die(new NotImplementedError("findByDestination"))
    def save(r: Route): IO[DomainError, Route]                                                      =
      ZIO.die(new NotImplementedError("save"))
    def update(r: Route): IO[DomainError, Route]                                                    =
      ZIO.die(new NotImplementedError("update"))
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                                     =
      ZIO.die(new NotImplementedError("delete"))

  def stubRouteRepo(
      onFindBySegment: (IataCode, IataCode) => IO[DomainError, Option[RouteWithAirportNames]] =
        unimplementedRouteRepo.findBySegment,
      onFindAll: Pagination => UIO[List[RouteWithAirportNames]] = unimplementedRouteRepo.findAll,
      onFindAllUnbounded: UIO[List[Route]] = unimplementedRouteRepo.findAllUnbounded,
      onFindByOrigin: (IataCode, Pagination) => IO[DomainError, List[RouteWithAirportNames]] =
        unimplementedRouteRepo.findByOrigin,
      onFindByDestination: (IataCode, Pagination) => IO[DomainError, List[RouteWithAirportNames]] =
        unimplementedRouteRepo.findByDestination,
      onSave: Route => IO[DomainError, Route] = unimplementedRouteRepo.save,
      onUpdate: Route => IO[DomainError, Route] = unimplementedRouteRepo.update,
      onDelete: (IataCode, IataCode) => IO[DomainError, Unit] = unimplementedRouteRepo.delete
  ): RouteRepository = new RouteRepository:
    def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[RouteWithAirportNames]]     =
      onFindBySegment(o, d)
    def findAll(p: Pagination): UIO[List[RouteWithAirportNames]]                                    = onFindAll(p)
    def findAllUnbounded: UIO[List[Route]]                                                          = onFindAllUnbounded
    def findByOrigin(o: IataCode, p: Pagination): IO[DomainError, List[RouteWithAirportNames]]      =
      onFindByOrigin(o, p)
    def findByDestination(d: IataCode, p: Pagination): IO[DomainError, List[RouteWithAirportNames]] =
      onFindByDestination(d, p)
    def save(r: Route): IO[DomainError, Route]                                                      = onSave(r)
    def update(r: Route): IO[DomainError, Route]                                                    = onUpdate(r)
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                                     = onDelete(o, d)
