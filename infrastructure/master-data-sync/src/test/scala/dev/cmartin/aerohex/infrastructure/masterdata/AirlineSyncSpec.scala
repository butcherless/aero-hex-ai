package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.*
import zio.nio.file.{Files, Path}
import zio.test.*

object AirlineSyncSpec extends ZIOSpecDefault:

  private def row(icao: String, name: String, callsign: String, country: String): String =
    s""""0","$name","\\N","XX","$icao","$callsign","$country","Y""""

  private val countries = List(Country(CountryCode("ES"), "Spain"), Country(CountryCode("FR"), "France"))

  private final case class StubUseCases(
      create: CreateAirlineUseCase,
      update: UpdateAirlineUseCase,
      delete: DeleteAirlineUseCase,
      find: FindAirlineUseCase,
      findCountry: FindCountryUseCase,
      currentState: UIO[List[Airline]]
  )

  private def stubUseCases(initial: List[Airline]): UIO[StubUseCases] =
    Ref.make(initial).map { state =>
      val create: CreateAirlineUseCase = (command: CreateAirlineCommand) =>
        val airline = Airline(command.icao, command.name, command.alias, command.callsign)
        state.update(airline :: _).as(airline)

      val update: UpdateAirlineUseCase = (command: UpdateAirlineCommand) =>
        val airline = Airline(command.icao, command.name, command.alias, command.callsign)
        state.update(_.map(a => if a.icao == command.icao then airline else a)).as(airline)

      val delete: DeleteAirlineUseCase = (icao: AirlineIcaoCode) => state.update(_.filterNot(_.icao == icao)).unit

      val find: FindAirlineUseCase = new FindAirlineUseCase:
        def findByIcao(icao: String): IO[DomainError, Airline]     =
          ZIO.die(new NotImplementedError("findByIcao"))
        def findAll(p: Pagination): IO[DomainError, List[Airline]] =
          ZIO.die(new NotImplementedError("findAll"))
        def findAllUnbounded: IO[DomainError, List[Airline]]       = state.get

      val findCountry: FindCountryUseCase = new FindCountryUseCase:
        def findByCode(code: CountryCode): IO[DomainError, Country] =
          ZIO.die(new NotImplementedError("findByCode"))
        def findAll(p: Pagination): UIO[List[Country]]              =
          ZIO.die(new NotImplementedError("findAll"))
        def findAllUnbounded: UIO[List[Country]]                    = ZIO.succeed(countries)
        def searchByName(q: String): UIO[List[Country]]             =
          ZIO.die(new NotImplementedError("searchByName"))

      StubUseCases(create, update, delete, find, findCountry, state.get)
    }

  private final case class CsvFixture(dir: Path, file: Path)

  private def writeDat(rows: List[String]): IO[java.io.IOException, CsvFixture] =
    for
      dir <- TempDirectory.create("airline-sync-spec-")
      file = dir / "airlines.dat"
      _   <- Files.writeLines(file, rows)
    yield CsvFixture(dir, file)

  private def runSync(fixture: CsvFixture, useCases: StubUseCases): IO[Throwable, SyncReport] =
    AirlineSync
      .sync(fixture.file)
      .provide(
        ZLayer.succeed(useCases.create),
        ZLayer.succeed(useCases.update),
        ZLayer.succeed(useCases.delete),
        ZLayer.succeed(useCases.find),
        ZLayer.succeed(useCases.findCountry)
      )

  override def spec: Spec[TestEnvironment, Any] =
    suite("AirlineSync")(
      test("creates a source-only airline not present in existing") {
        for
          fixture    <- writeDat(List(row("IBE", "Iberia", "IBERIA", "Spain")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA")))
        )
      },
      test("updates an existing airline whose name changed") {
        for
          fixture    <- writeDat(List(row("IBE", "Iberia", "IBERIA", "Spain")))
          useCases   <- stubUseCases(List(Airline(AirlineIcaoCode("IBE"), "Old Name", None, Some("IBERIA"))))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 1, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA")))
        )
      },
      test("deletes an existing airline absent from the source") {
        for
          fixture    <- writeDat(List(row("IBE", "Iberia", "IBERIA", "Spain")))
          useCases   <-
            stubUseCases(
              List(
                Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA")),
                Airline(AirlineIcaoCode("AFR"), "Air France", None, Some("AIRFRANS"))
              )
            )
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 1, unchanged = 1, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA")))
        )
      },
      // A row whose country name doesn't resolve fails at AirlineCsvParser.toCommand, counted as
      // skippedInvalid — unlike Country/Airport's parse-level tolerance tests, this one IS reachable
      // as a Left since country resolution happens after parse succeeds.
      test("tolerates a row whose country name doesn't resolve without aborting the rest of the sync") {
        for
          fixture    <-
            writeDat(List(row("IBE", "Iberia", "IBERIA", "Spain"), row("GHO", "Ghost Air", "GHOST", "Nowhereland")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 1, skippedConflict = 0),
          finalState == List(Airline(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA")))
        )
      }
    )
