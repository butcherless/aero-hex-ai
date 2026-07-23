package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.country.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.*
import zio.nio.file.{Files, Path}
import zio.test.*

object CountrySyncSpec extends ZIOSpecDefault:

  private final case class StubUseCases(
      create: CreateCountryUseCase,
      update: UpdateCountryUseCase,
      delete: DeleteCountryUseCase,
      find: FindCountryUseCase,
      currentState: UIO[List[Country]]
  )

  private def stubUseCases(initial: List[Country]): UIO[StubUseCases] =
    Ref.make(initial).map { state =>
      val create: CreateCountryUseCase = (command: CreateCountryCommand) =>
        val country = Country(command.code, command.name)
        state.update(country :: _).as(country)

      val update: UpdateCountryUseCase = (command: UpdateCountryCommand) =>
        val country = Country(command.code, command.name)
        state.update(_.map(c => if c.code == command.code then country else c)).as(country)

      val delete: DeleteCountryUseCase = (code: CountryCode) => state.update(_.filterNot(_.code == code)).unit

      val find: FindCountryUseCase = new FindCountryUseCase:
        def findByCode(code: CountryCode): IO[DomainError, Country] =
          ZIO.die(new NotImplementedError("findByCode"))
        def findAll(p: Pagination): UIO[List[Country]]              =
          ZIO.die(new NotImplementedError("findAll"))
        def findAllUnbounded: UIO[List[Country]]                    = state.get
        def searchByName(q: String): UIO[List[Country]]             =
          ZIO.die(new NotImplementedError("searchByName"))

      StubUseCases(create, update, delete, find, state.get)
    }

  private final case class CsvFixture(dir: Path, file: Path)

  private def writeCsv(lines: List[String]): IO[java.io.IOException, CsvFixture] =
    for
      dir <- TempDirectory.create("country-sync-spec-")
      file = dir / "countries.csv"
      _   <- Files.writeLines(file, lines)
    yield CsvFixture(dir, file)

  private def runSync(fixture: CsvFixture, useCases: StubUseCases): IO[java.io.IOException, SyncReport] =
    CountrySync
      .sync(fixture.file)
      .provide(
        ZLayer.succeed(useCases.create),
        ZLayer.succeed(useCases.update),
        ZLayer.succeed(useCases.delete),
        ZLayer.succeed(useCases.find)
      )

  override def spec: Spec[TestEnvironment, Any] =
    suite("CountrySync")(
      test("creates a source-only country not present in existing") {
        for
          fixture    <- writeCsv(List("Name,Code", "Spain,ES"))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Country(CountryCode("ES"), "Spain"))
        )
      },
      test("updates an existing country whose name changed") {
        for
          fixture    <- writeCsv(List("Name,Code", "Spain,ES"))
          useCases   <- stubUseCases(List(Country(CountryCode("ES"), "Old Spain")))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 1, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Country(CountryCode("ES"), "Spain"))
        )
      },
      test("deletes an existing country absent from the source") {
        for
          fixture    <- writeCsv(List("Name,Code", "Spain,ES"))
          useCases   <- stubUseCases(List(Country(CountryCode("ES"), "Spain"), Country(CountryCode("FR"), "France")))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 1, unchanged = 1, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Country(CountryCode("ES"), "Spain"))
        )
      },
      // "BadRow,XYZ" fails CountryCsvParser.parse's own regex (a 3-letter code never matches
      // [A-Za-z]{2}) and is dropped — logged by parse itself — before it ever becomes a CountryRow,
      // so it never reaches toCommand and CountrySync never sees it as a Left. skippedInvalid only
      // counts toCommand-stage failures (per the design), so it stays 0 here; what this test actually
      // verifies is that a malformed line doesn't abort the rest of the sync.
      test("tolerates a malformed CSV line without aborting the rest of the sync") {
        for
          fixture    <- writeCsv(List("Name,Code", "Spain,ES", "BadRow,XYZ"))
          useCases   <- stubUseCases(List(Country(CountryCode("ES"), "Spain")))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 0, unchanged = 1, skippedInvalid = 0, skippedConflict = 0),
          finalState == List(Country(CountryCode("ES"), "Spain"))
        )
      }
    )
