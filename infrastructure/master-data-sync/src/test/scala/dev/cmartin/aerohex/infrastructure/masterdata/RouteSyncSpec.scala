package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.*
import dev.cmartin.aerohex.shared.Pagination
import zio.*
import zio.nio.file.{Files, Path}
import zio.test.*

object RouteSyncSpec extends ZIOSpecDefault:

  private val mad = Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Barajas", "Madrid", 40.4719, -3.5626)
  private val bcn = Airport(IataCode("BCN"), AirportIcaoCode("LEBL"), "El Prat", "Barcelona", 41.2971, 2.0785)

  private val expectedMadBcnDistance =
    DistanceCalculator.haversineKm(mad.latitude, mad.longitude, bcn.latitude, bcn.longitude).round.toInt

  // routes.dat has no header row; columns: Airline, AirlineID, SourceAirport, SourceAirportID,
  // DestinationAirport, DestinationAirportID, Codeshare, Stops, Equipment.
  private def row(source: String, destination: String, airline: String = "IB"): String =
    s"""$airline,0,$source,0,$destination,0,,0,320"""

  private def findAirportStub(airports: List[Airport]): FindAirportUseCase = new FindAirportUseCase:
    def findByIata(iata: String): IO[DomainError, Airport]                         =
      ZIO.fromOption(airports.find(_.iataCode.value == iata)).orElseFail(DomainError.AirportNotFound(iata))
    def findAll(p: Pagination): IO[DomainError, List[Airport]]                     =
      ZIO.die(new NotImplementedError("findAll"))
    def findAllUnbounded: IO[DomainError, List[Airport]]                           = ZIO.succeed(airports)
    def findAllUnboundedWithCountry: IO[DomainError, List[(Airport, CountryCode)]] =
      ZIO.die(new NotImplementedError("findAllUnboundedWithCountry"))
    def searchByName(q: String): IO[DomainError, List[Airport]]                    =
      ZIO.die(new NotImplementedError("searchByName"))

  private final case class StubUseCases(
      create: CreateRouteUseCase,
      update: UpdateRouteUseCase,
      delete: DeleteRouteUseCase,
      find: FindRouteUseCase,
      currentState: UIO[List[Route]]
  )

  private def stubUseCases(initial: List[Route]): UIO[StubUseCases] =
    Ref.make(initial).map { state =>
      val create: CreateRouteUseCase = (command: CreateRouteCommand) =>
        val route =
          Route(
            IataCode.unsafeMake(command.originIata),
            IataCode.unsafeMake(command.destinationIata),
            command.distanceKm
          )
        state.update(route :: _).as(route)

      val update: UpdateRouteUseCase = (command: UpdateRouteCommand) =>
        val route =
          Route(
            IataCode.unsafeMake(command.originIata),
            IataCode.unsafeMake(command.destinationIata),
            command.distanceKm
          )
        state
          .update(_.map(r => if r.origin == route.origin && r.destination == route.destination then route else r))
          .as(route)

      val delete: DeleteRouteUseCase = (origin: IataCode, destination: IataCode) =>
        state.update(_.filterNot(r => r.origin == origin && r.destination == destination)).unit

      val find: FindRouteUseCase = new FindRouteUseCase:
        def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Route] =
          ZIO.die(new NotImplementedError("findBySegment"))
        def findAll(p: Pagination): IO[DomainError, List[Route]]                           =
          ZIO.die(new NotImplementedError("findAll"))
        def findAllUnbounded: IO[DomainError, List[Route]]                                 = state.get

      StubUseCases(create, update, delete, find, state.get)
    }

  private final case class DatFixture(dir: Path, file: Path)

  private def writeDat(rows: List[String]): IO[java.io.IOException, DatFixture] =
    for
      dir <- TempDirectory.create("route-sync-spec-")
      file = dir / "routes.dat"
      _   <- Files.writeLines(file, rows)
    yield DatFixture(dir, file)

  private def runSync(fixture: DatFixture, useCases: StubUseCases, airports: List[Airport]): IO[Throwable, SyncReport] =
    RouteSync
      .sync(fixture.file)
      .provide(
        ZLayer.succeed(useCases.create),
        ZLayer.succeed(useCases.update),
        ZLayer.succeed(useCases.delete),
        ZLayer.succeed(useCases.find),
        ZLayer.succeed(findAirportStub(airports))
      )

  override def spec: Spec[TestEnvironment, Any] =
    suite("RouteSync")(
      test("creates a source-only route not present in existing, with distance computed via Haversine") {
        for
          fixture    <- writeDat(List(row("MAD", "BCN")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance))
        )
      },
      test("updates an existing route whose distance changed") {
        for
          fixture    <- writeDat(List(row("MAD", "BCN")))
          useCases   <- stubUseCases(List(Route(IataCode("MAD"), IataCode("BCN"), 1)))
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 1, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance))
        )
      },
      test("reports fully unchanged on an idempotent rerun") {
        for
          fixture  <- writeDat(List(row("MAD", "BCN")))
          useCases <- stubUseCases(List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance)))
          report   <- runSync(fixture, useCases, List(mad, bcn))
          _        <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 0, unchanged = 1, skippedInvalid = 0, skippedConflict = 0)
        )
      },
      test("deletes an existing route absent from the source") {
        for
          fixture    <- writeDat(Nil)
          useCases   <- stubUseCases(List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance)))
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 1, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState.isEmpty
        )
      },
      test("skips a row referencing an unknown airport without aborting the rest of the sync") {
        for
          fixture    <- writeDat(List(row("MAD", "BCN"), row("MAD", "XXX")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 1, skippedConflict = 0),
          finalState == List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance))
        )
      },
      test("dedups a source-only origin/destination pair listed once per operating airline") {
        for
          fixture    <- writeDat(List(row("MAD", "BCN", "IB"), row("MAD", "BCN", "VY")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 1, skippedConflict = 0),
          finalState == List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance))
        )
      },
      // Filtered out by RouteCsvParser itself (Stops > 0), so it never reaches RouteSync as a row
      // to resolve — skippedInvalid stays 0 here, same nuance AirportSyncSpec's filtered-type test
      // documents for a non-large/medium airport row.
      test("tolerates a multi-leg row without aborting the rest of the sync") {
        val multiLeg = """IB,0,MAD,0,JFK,0,,1,332"""
        for
          fixture    <- writeDat(List(row("MAD", "BCN"), multiLeg))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases, List(mad, bcn))
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Route(IataCode("MAD"), IataCode("BCN"), expectedMadBcnDistance))
        )
      }
    )
