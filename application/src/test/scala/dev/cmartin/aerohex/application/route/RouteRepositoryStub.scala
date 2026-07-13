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
    def findAll(p: Pagination): IO[DomainError, List[Route]]                   =
      ZIO.die(new NotImplementedError("findAll"))
    def save(r: Route): IO[DomainError, Route]                                 =
      ZIO.die(new NotImplementedError("save"))
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                =
      ZIO.die(new NotImplementedError("delete"))

  def stubRouteRepo(
      onFindBySegment: (IataCode, IataCode) => IO[DomainError, Option[Route]] =
        unimplementedRouteRepo.findBySegment,
      onFindAll: Pagination => IO[DomainError, List[Route]] = unimplementedRouteRepo.findAll,
      onSave: Route => IO[DomainError, Route] = unimplementedRouteRepo.save,
      onDelete: (IataCode, IataCode) => IO[DomainError, Unit] = unimplementedRouteRepo.delete
  ): RouteRepository = new RouteRepository:
    def findBySegment(o: IataCode, d: IataCode): IO[DomainError, Option[Route]] = onFindBySegment(o, d)
    def findAll(p: Pagination): IO[DomainError, List[Route]]                   = onFindAll(p)
    def save(r: Route): IO[DomainError, Route]                                 = onSave(r)
    def delete(o: IataCode, d: IataCode): IO[DomainError, Unit]                = onDelete(o, d)
