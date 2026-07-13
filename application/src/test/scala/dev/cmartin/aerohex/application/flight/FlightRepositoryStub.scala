package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode, FlightRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — see AircraftRepositoryStub for the
// rationale (a call a test doesn't expect should surface loudly, not silently return a default).
private[application] object FlightRepositoryStub:

  val unimplementedFlightRepo: FlightRepository = new FlightRepository:
    def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] =
      ZIO.die(new NotImplementedError("findByCode"))
    def findAll(p: Pagination): IO[DomainError, List[Flight]]         =
      ZIO.die(new NotImplementedError("findAll"))
    def save(f: Flight): IO[DomainError, Flight]                      =
      ZIO.die(new NotImplementedError("save"))
    def update(f: Flight): IO[DomainError, Flight]                    =
      ZIO.die(new NotImplementedError("update"))
    def delete(code: FlightCode): IO[DomainError, Unit]               =
      ZIO.die(new NotImplementedError("delete"))

  def stubFlightRepo(
      onFindByCode: FlightCode => IO[DomainError, Option[Flight]] = unimplementedFlightRepo.findByCode,
      onFindAll: Pagination => IO[DomainError, List[Flight]] = unimplementedFlightRepo.findAll,
      onSave: Flight => IO[DomainError, Flight] = unimplementedFlightRepo.save,
      onUpdate: Flight => IO[DomainError, Flight] = unimplementedFlightRepo.update,
      onDelete: FlightCode => IO[DomainError, Unit] = unimplementedFlightRepo.delete
  ): FlightRepository = new FlightRepository:
    def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] = onFindByCode(code)
    def findAll(p: Pagination): IO[DomainError, List[Flight]]         = onFindAll(p)
    def save(f: Flight): IO[DomainError, Flight]                      = onSave(f)
    def update(f: Flight): IO[DomainError, Flight]                    = onUpdate(f)
    def delete(code: FlightCode): IO[DomainError, Unit]               = onDelete(code)
