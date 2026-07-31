package dev.cmartin.aerohex.infrastructure.masterdata

import zio.nio.file.Files
import zio.test.*

object RouteCsvParserSpec extends ZIOSpecDefault:

  // routes.dat has no header row; columns: Airline, AirlineID, SourceAirport, SourceAirportID,
  // DestinationAirport, DestinationAirportID, Codeshare, Stops, Equipment.
  private val direct      = """IB,2822,MAD,1229,BCN,1218,,0,320"""
  private val multiLeg    = """IB,2822,MAD,1229,JFK,3797,,1,332"""
  private val blankSource = """IB,2822,,1229,BCN,1218,,0,320"""
  private val blankDest   = """IB,2822,MAD,1229,,1218,,0,320"""

  private def parseRows(rows: List[String]) =
    for
      dir  <- TempDirectory.create("route-csv-parser-spec-")
      file  = dir / "routes.dat"
      _    <- Files.writeLines(file, rows)
      rows <- RouteCsvParser.parse(file)
      _    <- TempDirectory.delete(dir)
    yield rows

  override def spec: Spec[TestEnvironment, Any] =
    suite("RouteCsvParser")(
      test("parses a well-formed direct route row") {
        for rows <- parseRows(List(direct))
        yield assertTrue(rows == List(RouteRow("MAD", "BCN")))
      },
      test("silently filters out a multi-leg row (Stops > 0)") {
        for rows <- parseRows(List(direct, multiLeg))
        yield assertTrue(rows == List(RouteRow("MAD", "BCN")))
      },
      test("logs and skips a row with a blank source airport") {
        for rows <- parseRows(List(direct, blankSource))
        yield assertTrue(rows == List(RouteRow("MAD", "BCN")))
      },
      test("logs and skips a row with a blank destination airport") {
        for rows <- parseRows(List(direct, blankDest))
        yield assertTrue(rows == List(RouteRow("MAD", "BCN")))
      }
    )
