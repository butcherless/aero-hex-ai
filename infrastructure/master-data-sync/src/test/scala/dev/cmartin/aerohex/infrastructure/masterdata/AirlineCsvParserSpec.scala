package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airline.{AirlineIcaoCode, CreateAirlineCommand}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.nio.file.Files
import zio.test.*

object AirlineCsvParserSpec extends ZIOSpecDefault:

  private val iberia        = """"1","Iberia","\N","IB","IBE","IBERIA","Spain","Y""""
  private val blankIcao     = """"2","Nowhere Air","\N","NA","","","Spain","Y""""
  private val inactive      = """"3","Dead Airline","\N","DA","DAA","DEAD","Spain","N""""
  private val aliasCallsign = """"4","Vueling Airlines","Vueling","VY","VLG","VUELING","Spain","Y""""
  private val badCountry    = """"5","Ghost Air","\N","GA","GHO","GHOST","Nowhereland","Y""""

  private val countryNameToCode = Map("Spain" -> CountryCode("ES"))

  private def parseRows(rows: List[String]) =
    for
      dir  <- TempDirectory.create("airline-csv-parser-spec-")
      file  = dir / "airlines.dat"
      _    <- Files.writeLines(file, rows)
      rows <- AirlineCsvParser.parse(file)
      _    <- TempDirectory.delete(dir)
    yield rows

  override def spec: Spec[TestEnvironment, Any] =
    suite("AirlineCsvParser")(
      test("parses a well-formed active row with a callsign and no alias") {
        for rows <- parseRows(List(iberia))
        yield assertTrue(rows == List(AirlineRow("IBE", "Iberia", None, Some("IBERIA"), "Spain")))
      },
      test("parses alias and callsign when both are present") {
        for rows <- parseRows(List(aliasCallsign))
        yield assertTrue(
          rows == List(AirlineRow("VLG", "Vueling Airlines", Some("Vueling"), Some("VUELING"), "Spain"))
        )
      },
      test("silently filters out an inactive row") {
        for rows <- parseRows(List(iberia, inactive))
        yield assertTrue(rows.size == 1, rows.head.icao == "IBE")
      },
      test("logs and skips a row with a blank ICAO code") {
        for rows <- parseRows(List(iberia, blankIcao))
        yield assertTrue(rows.size == 1, rows.head.icao == "IBE")
      },
      test("toCommand builds a valid CreateAirlineCommand from a well-formed row") {
        for command <-
            AirlineCsvParser.toCommand(AirlineRow("IBE", "Iberia", None, Some("IBERIA"), "Spain"), countryNameToCode)
        yield assertTrue(
          command == CreateAirlineCommand(AirlineIcaoCode("IBE"), "Iberia", None, Some("IBERIA"), CountryCode("ES"))
        )
      },
      test("toCommand fails with InvalidAirlineIcaoCode when the ICAO code is the wrong length") {
        for error <- AirlineCsvParser.toCommand(AirlineRow("IB", "Iberia", None, None, "Spain"), countryNameToCode).flip
        yield assertTrue(error match
          case DomainError.InvalidAirlineIcaoCode(errors) => errors.size == 1
          case _                                          => false)
      },
      test("toCommand fails with CountryNotFound when the country name doesn't resolve") {
        for
          rows  <- parseRows(List(badCountry))
          error <- AirlineCsvParser.toCommand(rows.head, countryNameToCode).flip
        yield assertTrue(error == DomainError.CountryNotFound("Nowhereland"))
      }
    )
