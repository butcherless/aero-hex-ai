package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.*
import zio.nio.file.{Files, Path}
import zio.test.*

object AirportSyncSpec extends ZIOSpecDefault:

  private val header =
    "id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region," +
      "municipality,scheduled_service,icao_code,iata_code,gps_code,local_code,home_link,wikipedia_link,keywords"

  private def row(iata: String, icao: String, name: String, city: String, country: String): String =
    s"""0,"$icao","large_airport","$name",0,0,0,"EU","$country","$country-X","$city","yes","$icao","$iata","$icao",,,,"""

  private final case class StubUseCases(
      create: CreateAirportUseCase,
      update: UpdateAirportUseCase,
      delete: DeleteAirportUseCase,
      find: FindAirportUseCase,
      currentState: UIO[List[Airport]]
  )

  private def stubUseCases(initial: List[Airport]): UIO[StubUseCases] =
    Ref.make(initial).map { state =>
      val create: CreateAirportUseCase = (command: CreateAirportCommand) =>
        val airport = Airport(command.iataCode, command.icaoCode, command.name, command.city)
        state.update(airport :: _).as(airport)

      val update: UpdateAirportUseCase = (command: UpdateAirportCommand) =>
        val airport = Airport(command.iataCode, command.icaoCode, command.name, command.city)
        state.update(_.map(a => if a.iataCode == command.iataCode then airport else a)).as(airport)

      val delete: DeleteAirportUseCase = (iata: IataCode) => state.update(_.filterNot(_.iataCode == iata)).unit

      val find: FindAirportUseCase = new FindAirportUseCase:
        def findByIata(iata: String): IO[DomainError, Airport]      =
          ZIO.die(new NotImplementedError("findByIata"))
        def findAll(p: Pagination): IO[DomainError, List[Airport]]  =
          ZIO.die(new NotImplementedError("findAll"))
        def findAllUnbounded: IO[DomainError, List[Airport]]        = state.get
        def searchByName(q: String): IO[DomainError, List[Airport]] =
          ZIO.die(new NotImplementedError("searchByName"))

      StubUseCases(create, update, delete, find, state.get)
    }

  private final case class CsvFixture(dir: Path, file: Path)

  private def writeCsv(rows: List[String]): IO[java.io.IOException, CsvFixture] =
    for
      dir <- TempDirectory.create("airport-sync-spec-")
      file = dir / "airports.csv"
      _   <- Files.writeLines(file, header :: rows)
    yield CsvFixture(dir, file)

  private def runSync(fixture: CsvFixture, useCases: StubUseCases): IO[Throwable, SyncReport] =
    AirportSync
      .sync(fixture.file)
      .provide(
        ZLayer.succeed(useCases.create),
        ZLayer.succeed(useCases.update),
        ZLayer.succeed(useCases.delete),
        ZLayer.succeed(useCases.find)
      )

  override def spec: Spec[TestEnvironment, Any] =
    suite("AirportSync")(
      test("creates a source-only airport not present in existing") {
        for
          fixture    <- writeCsv(List(row("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES")))
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState ==
            List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas Airport", "Madrid"))
        )
      },
      test("updates an existing airport whose name changed") {
        for
          fixture    <- writeCsv(List(row("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES")))
          useCases   <- stubUseCases(List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Old Name", "Madrid")))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 1, deleted = 0, unchanged = 0, skippedInvalid = 0, skippedConflict = 0),
          finalState ==
            List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas Airport", "Madrid"))
        )
      },
      test("deletes an existing airport absent from the source") {
        for
          fixture    <- writeCsv(List(row("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES")))
          useCases   <- stubUseCases(
                          List(
                            Airport(
                              IataCode("MAD"),
                              AirportIcaoCode("LEMD"),
                              "Adolfo Suárez Madrid-Barajas Airport",
                              "Madrid"
                            ),
                            Airport(
                              IataCode("BCN"),
                              AirportIcaoCode("LEBL"),
                              "Josep Tarradellas Barcelona-El Prat",
                              "Barcelona"
                            )
                          )
                        )
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 1, unchanged = 1, skippedInvalid = 0, skippedConflict = 0),
          finalState ==
            List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas Airport", "Madrid"))
        )
      },
      // A row filtered out by AirportCsvParser.parse's own type filter (§9) never becomes an
      // AirportRow, so it never reaches toCommand and AirportSync never sees it as a Left —
      // skippedInvalid stays 0 here, same nuance CountrySyncSpec's equivalent test documents.
      test("tolerates a filtered-out row without aborting the rest of the sync") {
        val heliport =
          """0,"00A","heliport","Total RF Heliport",0,0,0,"NA","US","US-X","Bensalem","no",,,"K00A","00A",,,"""
        for
          fixture    <-
            writeCsv(List(row("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES"), heliport))
          useCases   <- stubUseCases(List(Airport(
                          IataCode("MAD"),
                          AirportIcaoCode("LEMD"),
                          "Adolfo Suárez Madrid-Barajas Airport",
                          "Madrid"
                        )))
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 0, updated = 0, deleted = 0, unchanged = 1, skippedInvalid = 0, skippedConflict = 0),
          finalState ==
            List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas Airport", "Madrid"))
        )
      },
      // Unlike the filtered-out row above, a non-blank but malformed IATA code (§8) survives
      // AirportCsvParser.parse (which only checks blank/shape on ICAO, not IATA) and reaches
      // toCommand, where IataCode.validateAll fails it — this IS counted as skippedInvalid.
      test("tolerates a row whose IATA code fails toCommand validation without aborting the rest of the sync") {
        for
          fixture    <- writeCsv(
                          List(
                            row("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES"),
                            row("MA", "GHOI", "Ghost Airport", "Nowhere", "ES")
                          )
                        )
          useCases   <- stubUseCases(Nil)
          report     <- runSync(fixture, useCases)
          finalState <- useCases.currentState
          _          <- TempDirectory.delete(fixture.dir)
        yield assertTrue(
          report ==
            SyncReport(created = 1, updated = 0, deleted = 0, unchanged = 0, skippedInvalid = 1, skippedConflict = 0),
          finalState ==
            List(Airport(IataCode("MAD"), AirportIcaoCode("LEMD"), "Adolfo Suárez Madrid-Barajas Airport", "Madrid"))
        )
      }
    )
