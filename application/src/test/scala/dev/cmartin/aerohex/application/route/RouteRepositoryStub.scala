package dev.cmartin.aerohex.application.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{Route, RouteRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — see AirlineRepositoryStub for the
// rationale (a call a test doesn't expect should surface loudly, not silently return a default).
private[application] object RouteRepositoryStub:

  val unimplementedRouteRepo: RouteRepository = new RouteRepository:
    def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[Route]] =
      ZIO.die(new NotImplementedError("findBySegment"))
    def findAll(p: Pagination): IO[DomainError, List[Route]]                    =
      ZIO.die(new NotImplementedError("findAll"))
    def findAllUnbounded: IO[DomainError, List[Route]]                          =
      ZIO.die(new NotImplementedError("findAllUnbounded"))
    def save(r: Route): IO[DomainError, Route]                                  =
      ZIO.die(new NotImplementedError("save"))
    def update(r: Route): IO[DomainError, Route]                                =
      ZIO.die(new NotImplementedError("update"))
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                 =
      ZIO.die(new NotImplementedError("delete"))

  def stubRouteRepo(
      onFindBySegment: (IataCode, IataCode) => IO[DomainError, Option[Route]] =
        unimplementedRouteRepo.findBySegment,
      onFindAll: Pagination => IO[DomainError, List[Route]] = unimplementedRouteRepo.findAll,
      onFindAllUnbounded: IO[DomainError, List[Route]] = unimplementedRouteRepo.findAllUnbounded,
      onSave: Route => IO[DomainError, Route] = unimplementedRouteRepo.save,
      onUpdate: Route => IO[DomainError, Route] = unimplementedRouteRepo.update,
      onDelete: (IataCode, IataCode) => IO[DomainError, Unit] = unimplementedRouteRepo.delete
  ): RouteRepository = new RouteRepository:
    def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[Route]] = onFindBySegment(o, d)
    def findAll(p: Pagination): IO[DomainError, List[Route]]                    = onFindAll(p)
    def findAllUnbounded: IO[DomainError, List[Route]]                          = onFindAllUnbounded
    def save(r: Route): IO[DomainError, Route]                                  = onSave(r)
    def update(r: Route): IO[DomainError, Route]                                = onUpdate(r)
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                 = onDelete(o, d)
