package dev.cmartin.aerohex.application.aircraft

import dev.cmartin.aerohex.domain.aircraft.{Aircraft, AircraftRepository, Registration}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, ZIO}

// Repository stub where every method dies unless overridden — any call a test doesn't
// expect surfaces as a loud failure instead of a silently wrong default. stubAircraftRepo
// lets each test override just the one method it exercises instead of re-declaring the
// full interface.
private[application] object AircraftRepositoryStub:

  val unimplementedAircraftRepo: AircraftRepository = new AircraftRepository:
    def findByRegistration(reg: Registration): IO[DomainError, Option[Aircraft]] =
      ZIO.die(new NotImplementedError("findByRegistration"))
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]                  =
      ZIO.die(new NotImplementedError("findAll"))
    def save(a: Aircraft): IO[DomainError, Aircraft]                             =
      ZIO.die(new NotImplementedError("save"))
    def update(a: Aircraft): IO[DomainError, Aircraft]                           =
      ZIO.die(new NotImplementedError("update"))
    def delete(reg: Registration): IO[DomainError, Unit]                         =
      ZIO.die(new NotImplementedError("delete"))

  def stubAircraftRepo(
      onFindByRegistration: Registration => IO[DomainError, Option[Aircraft]] =
        unimplementedAircraftRepo.findByRegistration,
      onFindAll: Pagination => IO[DomainError, List[Aircraft]] = unimplementedAircraftRepo.findAll,
      onSave: Aircraft => IO[DomainError, Aircraft] = unimplementedAircraftRepo.save,
      onUpdate: Aircraft => IO[DomainError, Aircraft] = unimplementedAircraftRepo.update,
      onDelete: Registration => IO[DomainError, Unit] = unimplementedAircraftRepo.delete
  ): AircraftRepository = new AircraftRepository:
    def findByRegistration(reg: Registration): IO[DomainError, Option[Aircraft]] = onFindByRegistration(reg)
    def findAll(p: Pagination): IO[DomainError, List[Aircraft]]                  = onFindAll(p)
    def save(a: Aircraft): IO[DomainError, Aircraft]                             = onSave(a)
    def update(a: Aircraft): IO[DomainError, Aircraft]                           = onUpdate(a)
    def delete(reg: Registration): IO[DomainError, Unit]                         = onDelete(reg)
