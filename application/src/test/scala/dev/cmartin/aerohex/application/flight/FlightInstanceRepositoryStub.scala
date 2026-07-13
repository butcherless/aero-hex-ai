package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{FlightInstance, FlightInstanceId, FlightInstanceRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — see AircraftRepositoryStub for the
// rationale (a call a test doesn't expect should surface loudly, not silently return a default).
private[application] object FlightInstanceRepositoryStub:

  val unimplementedFlightInstanceRepo: FlightInstanceRepository = new FlightInstanceRepository:
    def findById(id: FlightInstanceId): IO[DomainError, Option[FlightInstance]] =
      ZIO.die(new NotImplementedError("findById"))
    def findAll(p: Pagination): IO[DomainError, List[FlightInstance]]           =
      ZIO.die(new NotImplementedError("findAll"))
    def save(j: FlightInstance): IO[DomainError, FlightInstance]                =
      ZIO.die(new NotImplementedError("save"))
    def delete(id: FlightInstanceId): IO[DomainError, Unit]                     =
      ZIO.die(new NotImplementedError("delete"))

  def stubFlightInstanceRepo(
      onFindById: FlightInstanceId => IO[DomainError, Option[FlightInstance]] =
        unimplementedFlightInstanceRepo.findById,
      onFindAll: Pagination => IO[DomainError, List[FlightInstance]] = unimplementedFlightInstanceRepo.findAll,
      onSave: FlightInstance => IO[DomainError, FlightInstance] = unimplementedFlightInstanceRepo.save,
      onDelete: FlightInstanceId => IO[DomainError, Unit] = unimplementedFlightInstanceRepo.delete
  ): FlightInstanceRepository = new FlightInstanceRepository:
    def findById(id: FlightInstanceId): IO[DomainError, Option[FlightInstance]] = onFindById(id)
    def findAll(p: Pagination): IO[DomainError, List[FlightInstance]]           = onFindAll(p)
    def save(j: FlightInstance): IO[DomainError, FlightInstance]                = onSave(j)
    def delete(id: FlightInstanceId): IO[DomainError, Unit]                     = onDelete(id)
