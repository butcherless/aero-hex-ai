package dev.cmartin.aerohex.application.aircraft

import AircraftRepositoryStub.{stubAircraftRepo, unimplementedAircraftRepo}
import dev.cmartin.aerohex.domain.aircraft.{
  Aircraft,
  CreateAircraftCommand,
  CreateAircraftUseCase,
  DeleteAircraftUseCase,
  FindAircraftUseCase,
  Registration,
  UpdateAircraftCommand,
  UpdateAircraftUseCase
}
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.test.*
import zio.{Ref, Scope, ZIO, ZLayer}

object AircraftServiceSpec extends ZIOSpecDefault:

  private val ecMig = Aircraft(Registration("EC-MIG"), "B788", "Boeing 787-8", AirlineIcaoCode("IBE"))
  private val ecAbc = Aircraft(Registration("EC-ABC"), "A320", "Airbus A320", AirlineIcaoCode("VLG"))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Aircraft application services")(
      suite("CreateAircraftService")(
        test("saves and returns the new aircraft when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Aircraft]](None)
            repo      = stubAircraftRepo(
                          onFindByRegistration = _ => ZIO.none,
                          onSave = a => savedRef.set(Some(a)).as(a)
                        )
            command   = CreateAircraftCommand(Registration("EC-MIG"), "B788", "Boeing 787-8", AirlineIcaoCode("IBE"))
            result   <- new CreateAircraftService(repo).create(command)
            saved    <- savedRef.get
          yield assertTrue(
            result == ecMig,
            saved.contains(ecMig)
          )
        },
        test("fails with AircraftAlreadyExists and never calls save when the aircraft already exists") {
          val repo    = stubAircraftRepo(onFindByRegistration = _ => ZIO.some(ecMig))
          val command = CreateAircraftCommand(Registration("EC-MIG"), "B788", "Other name", AirlineIcaoCode("IBE"))
          for error <- new CreateAircraftService(repo).create(command).flip
          yield assertTrue(error == DomainError.AircraftAlreadyExists("EC-MIG"))
        }
      ),
      suite("FindAircraftService")(
        test("returns the aircraft when the repository finds it") {
          val repo = stubAircraftRepo(onFindByRegistration = _ => ZIO.some(ecMig))
          for result <- new FindAircraftService(repo).findByRegistration("EC-MIG")
          yield assertTrue(result == ecMig)
        },
        test("fails with AircraftNotFound when the repository has no match") {
          val repo = stubAircraftRepo(onFindByRegistration = _ => ZIO.none)
          for error <- new FindAircraftService(repo).findByRegistration("XXX").flip
          yield assertTrue(error == DomainError.AircraftNotFound("XXX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = stubAircraftRepo(onFindAll = _ => ZIO.succeed(List(ecMig, ecAbc)))
          for result <- new FindAircraftService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(ecMig, ecAbc))
        }
      ),
      suite("UpdateAircraftService")(
        test("builds the updated aircraft from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Aircraft]](None)
            repo         = stubAircraftRepo(onUpdate = a => capturedRef.set(Some(a)).as(a))
            command      =
              UpdateAircraftCommand(Registration("EC-MIG"), "B789", "Boeing 787-9", AirlineIcaoCode("IBE"))
            result      <- new UpdateAircraftService(repo).update(command)
            captured    <- capturedRef.get
          yield assertTrue(
            result == ecMig.copy(typeCode = "B789", description = "Boeing 787-9"),
            captured.contains(ecMig.copy(typeCode = "B789", description = "Boeing 787-9"))
          )
        },
        test("propagates AircraftNotFound from the repository") {
          val repo    = stubAircraftRepo(onUpdate = _ => ZIO.fail(DomainError.AircraftNotFound("XXX")))
          val command = UpdateAircraftCommand(Registration("XXX"), "B789", "Nowhere", AirlineIcaoCode("IBE"))
          for error <- new UpdateAircraftService(repo).update(command).flip
          yield assertTrue(error == DomainError.AircraftNotFound("XXX"))
        }
      ),
      suite("DeleteAircraftService")(
        test("delegates to the repository and succeeds") {
          val repo = stubAircraftRepo(onDelete = _ => ZIO.unit)
          for result <- new DeleteAircraftService(repo).delete(Registration("EC-MIG")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates AircraftNotFound from the repository") {
          val repo = stubAircraftRepo(onDelete = _ => ZIO.fail(DomainError.AircraftNotFound("XXX")))
          for error <- new DeleteAircraftService(repo).delete(Registration("XXX")).flip
          yield assertTrue(error == DomainError.AircraftNotFound("XXX"))
        }
      ),
      suite("Aircraft service layers")(
        test("CreateAircraftService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[CreateAircraftUseCase]
                     .provide(ZLayer.succeed(unimplementedAircraftRepo), CreateAircraftService.layer)
          yield assertCompletes
        },
        test("FindAircraftService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAircraftUseCase]
                     .provide(ZLayer.succeed(unimplementedAircraftRepo), FindAircraftService.layer)
          yield assertCompletes
        },
        test("UpdateAircraftService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[UpdateAircraftUseCase]
                     .provide(ZLayer.succeed(unimplementedAircraftRepo), UpdateAircraftService.layer)
          yield assertCompletes
        },
        test("DeleteAircraftService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[DeleteAircraftUseCase]
                     .provide(ZLayer.succeed(unimplementedAircraftRepo), DeleteAircraftService.layer)
          yield assertCompletes
        }
      )
    )
