package dev.cmartin.aerohex.application.flight

import FlightRepositoryStub.{stubFlightRepo, unimplementedFlightRepo}
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{
  CreateFlightCommand,
  CreateFlightUseCase,
  DeleteFlightUseCase,
  FindAirlineForFlightUseCase,
  FindFlightUseCase,
  FindFlightsByAirlineUseCase,
  Flight,
  FlightCode,
  UpdateFlightCommand,
  UpdateFlightUseCase
}
import dev.cmartin.aerohex.shared.Pagination
import java.time.LocalTime
import zio.test.*
import zio.{Ref, Scope, ZIO, ZLayer}

object FlightServiceSpec extends ZIOSpecDefault:

  private val ux9117 = Flight(
    FlightCode("UX9117"),
    Some("AEA9117"),
    LocalTime.of(7, 5),
    LocalTime.of(8, 55),
    IataCode("MAD"),
    IataCode("TFN"),
    AirlineIcaoCode("AEA")
  )

  private val airEuropa = Airline(AirlineIcaoCode("AEA"), "Air Europa", None, Some("AIR EUROPA"))

  private val vy1234 = Flight(
    FlightCode("VY1234"),
    None,
    LocalTime.of(10, 0),
    LocalTime.of(11, 30),
    IataCode("MAD"),
    IataCode("BCN"),
    AirlineIcaoCode("VLG")
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Flight application services")(
      suite("CreateFlightService")(
        test("saves and returns the new flight when it does not already exist") {
          for
            savedRef <- Ref.make[Option[Flight]](None)
            repo      = stubFlightRepo(
                          onFindByCode = _ => ZIO.none,
                          onSave = f => savedRef.set(Some(f)).as(f)
                        )
            command   = CreateFlightCommand(
                          FlightCode("UX9117"),
                          Some("AEA9117"),
                          LocalTime.of(7, 5),
                          LocalTime.of(8, 55),
                          IataCode("MAD"),
                          IataCode("TFN"),
                          AirlineIcaoCode("AEA")
                        )
            result   <- new CreateFlightService(repo).create(command)
            saved    <- savedRef.get
          yield assertTrue(
            result == ux9117,
            saved.contains(ux9117)
          )
        },
        test("fails with FlightAlreadyExists and never calls save when the flight already exists") {
          val repo    = stubFlightRepo(onFindByCode = _ => ZIO.some(ux9117))
          val command = CreateFlightCommand(
            FlightCode("UX9117"),
            None,
            LocalTime.of(7, 5),
            LocalTime.of(8, 55),
            IataCode("MAD"),
            IataCode("TFN"),
            AirlineIcaoCode("AEA")
          )
          for error <- new CreateFlightService(repo).create(command).flip
          yield assertTrue(error == DomainError.FlightAlreadyExists("UX9117"))
        }
      ),
      suite("FindFlightService")(
        test("returns the flight when the repository finds it") {
          val repo = stubFlightRepo(onFindByCode = _ => ZIO.some(ux9117))
          for result <- new FindFlightService(repo).findByCode("UX9117")
          yield assertTrue(result == ux9117)
        },
        test("fails with FlightNotFound when the repository has no match") {
          val repo = stubFlightRepo(onFindByCode = _ => ZIO.none)
          for error <- new FindFlightService(repo).findByCode("XXXXXX").flip
          yield assertTrue(error == DomainError.FlightNotFound("XXXXXX"))
        },
        test("findAll delegates to the repository unchanged") {
          val repo = stubFlightRepo(onFindAll = _ => ZIO.succeed(List(ux9117, vy1234)))
          for result <- new FindFlightService(repo).findAll(Pagination(1, 20))
          yield assertTrue(result == List(ux9117, vy1234))
        }
      ),
      suite("UpdateFlightService")(
        test("builds the updated flight from the command and returns the repository's result") {
          for
            capturedRef <- Ref.make[Option[Flight]](None)
            repo         = stubFlightRepo(onUpdate = f => capturedRef.set(Some(f)).as(f))
            command      = UpdateFlightCommand(
                             FlightCode("UX9117"),
                             Some("AEA9117"),
                             LocalTime.of(7, 30),
                             LocalTime.of(9, 15),
                             IataCode("MAD"),
                             IataCode("TFN"),
                             AirlineIcaoCode("AEA")
                           )
            result      <- new UpdateFlightService(repo).update(command)
            captured    <- capturedRef.get
          yield assertTrue(
            result == ux9117.copy(schedDeparture = LocalTime.of(7, 30), schedArrival = LocalTime.of(9, 15)),
            captured.contains(ux9117.copy(schedDeparture = LocalTime.of(7, 30), schedArrival = LocalTime.of(9, 15)))
          )
        },
        test("propagates FlightNotFound from the repository") {
          val repo    = stubFlightRepo(onUpdate = _ => ZIO.fail(DomainError.FlightNotFound("XXXXXX")))
          val command = UpdateFlightCommand(
            FlightCode("XXXXXX"),
            None,
            LocalTime.of(7, 30),
            LocalTime.of(9, 15),
            IataCode("MAD"),
            IataCode("TFN"),
            AirlineIcaoCode("AEA")
          )
          for error <- new UpdateFlightService(repo).update(command).flip
          yield assertTrue(error == DomainError.FlightNotFound("XXXXXX"))
        }
      ),
      suite("DeleteFlightService")(
        test("delegates to the repository and succeeds") {
          val repo = stubFlightRepo(onDelete = _ => ZIO.unit)
          for result <- new DeleteFlightService(repo).delete(FlightCode("UX9117")).exit
          yield assertTrue(result.isSuccess)
        },
        test("propagates FlightNotFound from the repository") {
          val repo = stubFlightRepo(onDelete = _ => ZIO.fail(DomainError.FlightNotFound("XXXXXX")))
          for error <- new DeleteFlightService(repo).delete(FlightCode("XXXXXX")).flip
          yield assertTrue(error == DomainError.FlightNotFound("XXXXXX"))
        }
      ),
      suite("FindFlightsByAirlineService")(
        test("delegates to the repository unchanged") {
          val repo = stubFlightRepo(onFindByAirline = (_, _) => ZIO.succeed(List(ux9117)))
          for result <- new FindFlightsByAirlineService(repo).findByAirline(AirlineIcaoCode("AEA"), Pagination(1, 20))
          yield assertTrue(result == List(ux9117))
        },
        test("returns an empty list for an airline with no flights") {
          val repo = stubFlightRepo(onFindByAirline = (_, _) => ZIO.succeed(Nil))
          for result <- new FindFlightsByAirlineService(repo).findByAirline(AirlineIcaoCode("VLG"), Pagination(1, 20))
          yield assertTrue(result.isEmpty)
        }
      ),
      suite("FindAirlineForFlightService")(
        test("returns the operating airline when the flight is found") {
          val repo = stubFlightRepo(onFindAirlineByCode = _ => ZIO.some(airEuropa))
          for result <- new FindAirlineForFlightService(repo).findAirline(FlightCode("UX9117"))
          yield assertTrue(result == airEuropa)
        },
        test("fails with FlightNotFound when the flight does not exist") {
          val repo = stubFlightRepo(onFindAirlineByCode = _ => ZIO.none)
          for error <- new FindAirlineForFlightService(repo).findAirline(FlightCode("XXXXXX")).flip
          yield assertTrue(error == DomainError.FlightNotFound("XXXXXX"))
        }
      ),
      suite("Flight service layers")(
        test("CreateFlightService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[CreateFlightUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), CreateFlightService.layer)
          yield assertCompletes
        },
        test("FindFlightService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindFlightUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), FindFlightService.layer)
          yield assertCompletes
        },
        test("UpdateFlightService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[UpdateFlightUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), UpdateFlightService.layer)
          yield assertCompletes
        },
        test("DeleteFlightService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[DeleteFlightUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), DeleteFlightService.layer)
          yield assertCompletes
        },
        test("FindFlightsByAirlineService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindFlightsByAirlineUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), FindFlightsByAirlineService.layer)
          yield assertCompletes
        },
        test("FindAirlineForFlightService.layer constructs a usable instance") {
          for _ <- ZIO
                     .service[FindAirlineForFlightUseCase]
                     .provide(ZLayer.succeed(unimplementedFlightRepo), FindAirlineForFlightService.layer)
          yield assertCompletes
        }
      )
    )
