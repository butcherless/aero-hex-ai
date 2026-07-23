package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.{AirportIcaoCode, CreateAirportCommand, IataCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.nio.file.Files
import zio.test.*

object AirportCsvParserSpec extends ZIOSpecDefault:

  private val header =
    "id,ident,type,name,latitude_deg,longitude_deg,elevation_ft,continent,iso_country,iso_region," +
      "municipality,scheduled_service,icao_code,iata_code,gps_code,local_code,home_link,wikipedia_link,keywords"

  // Baseline: large_airport, icao_code and ident agree.
  private val madrid =
    """3,"LEMD","large_airport","Adolfo Suárez Madrid-Barajas Airport",40.471926,-3.56264,1998,"EU","ES","ES-M",""" +
      """"Madrid","yes","LEMD","MAD","LEMD",,,,"""

  // Fallback path: medium_airport, icao_code blank, ident is a valid 4-letter code.
  private val fallback =
    """4,"GCXO","medium_airport","Tenerife North Airport",28.482639,-16.341389,2076,"EU","ES","ES-TF",""" +
      """"San Cristóbal de La Laguna","yes",,"TFN","GCXO",,,,"""

  // Filtered out silently: not large/medium.
  private val heliport =
    """5,"00A","heliport","Total RF Heliport",40.070985,-74.933689,11,"NA","US","US-PA",""" +
      """"Bensalem","no",,,"K00A","00A",,,"""

  // Skipped with a warning: blank iata_code.
  private val blankIata =
    """6,"AGGH","large_airport","Honiara International Airport",-9.428,160.054993,28,"OC","SB","SB-GU",""" +
      """"Honiara","yes","AGGH",,"AGGH",,,,"""

  // Skipped with a warning: iata_code is present, but neither icao_code nor ident (its fallback)
  // is a valid 4-letter shape.
  private val noValidIcao =
    """7,"AQ-0016","medium_airport","Grandfather Skiway",36.09,-81.75,3739,"NA","US","US-NC",""" +
      """"Boone","no",,"XYZ",,,,,"""

  private def parseRows(rows: List[String]) =
    for
      dir  <- TempDirectory.create("airport-csv-parser-spec-")
      file  = dir / "airports.csv"
      _    <- Files.writeLines(file, header :: rows)
      rows <- AirportCsvParser.parse(file)
      _    <- TempDirectory.delete(dir)
    yield rows

  override def spec: Spec[TestEnvironment, Any] =
    suite("AirportCsvParser")(
      test("parses a well-formed large_airport row") {
        for rows <- parseRows(List(madrid))
        yield assertTrue(rows ==
          List(AirportRow("MAD", "LEMD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "ES")))
      },
      test("falls back to ident when icao_code is blank") {
        for rows <- parseRows(List(fallback))
        yield assertTrue(
          rows == List(AirportRow("TFN", "GCXO", "Tenerife North Airport", "San Cristóbal de La Laguna", "ES"))
        )
      },
      test("silently filters out a row whose type is not large/medium airport") {
        for rows <- parseRows(List(madrid, heliport))
        yield assertTrue(rows.size == 1, rows.head.iataCode == "MAD")
      },
      test("logs and skips a row with a blank iata_code") {
        for rows <- parseRows(List(madrid, blankIata))
        yield assertTrue(rows.size == 1, rows.head.iataCode == "MAD")
      },
      test("logs and skips a row with no valid ICAO code in either column") {
        for rows <- parseRows(List(madrid, noValidIcao))
        yield assertTrue(rows.size == 1, rows.head.iataCode == "MAD")
      },
      test("toCommand builds a valid CreateAirportCommand from a well-formed row") {
        for command <- AirportCsvParser.toCommand(AirportRow("MAD", "LEMD", "Barajas", "Madrid", "ES"))
        yield assertTrue(
          command ==
            CreateAirportCommand(IataCode("MAD"), AirportIcaoCode("LEMD"), "Barajas", "Madrid", CountryCode("ES"))
        )
      },
      test("toCommand fails with InvalidAirportIcaoCode when the ICAO code is the wrong length") {
        for error <- AirportCsvParser.toCommand(AirportRow("MAD", "LEM", "Barajas", "Madrid", "ES")).flip
        yield assertTrue(error match
          case DomainError.InvalidAirportIcaoCode(errors) => errors.size == 1
          case _                                          => false)
      },
      // parse only rejects a blank iata_code (§8) — it never checks shape/length beyond that, so a
      // non-blank but malformed IATA code (e.g. numeric, or the wrong length) reaches toCommand.
      test("toCommand fails with InvalidIataCode when the IATA code is the wrong length") {
        for error <- AirportCsvParser.toCommand(AirportRow("MA", "LEMD", "Barajas", "Madrid", "ES")).flip
        yield assertTrue(error match
          case DomainError.InvalidIataCode(errors) => errors.size == 1
          case _                                   => false)
      }
    )
