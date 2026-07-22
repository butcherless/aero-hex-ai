package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.country.{CountryCode, CreateCountryCommand}
import dev.cmartin.aerohex.domain.error.DomainError
import zio.nio.file.Files
import zio.test.*

object CountryCsvParserSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("CountryCsvParser")(
      test("parses valid rows, skips the header, and preserves the quoted-comma edge case") {
        for
          dir  <- TempDirectory.create("country-csv-parser-spec-")
          file  = dir / "countries.csv"
          _    <- Files.writeLines(
                    file,
                    List(
                      "Name,Code",
                      "Afghanistan,AF",
                      "\"Bonaire, Sint Eustatius and Saba\",BQ"
                    )
                  )
          rows <- CountryCsvParser.parse(file)
          _    <- TempDirectory.delete(dir)
        yield assertTrue(
          rows == List(
            CountryRow("Afghanistan", "AF"),
            CountryRow("Bonaire, Sint Eustatius and Saba", "BQ")
          )
        )
      },
      test("tolerates a malformed line by skipping it, not aborting the file") {
        for
          dir  <- TempDirectory.create("country-csv-parser-spec-")
          file  = dir / "countries.csv"
          _    <- Files.writeLines(
                    file,
                    List("Name,Code", "Afghanistan,AF", "BadRow,XYZ", "Albania,AL")
                  )
          rows <- CountryCsvParser.parse(file)
          _    <- TempDirectory.delete(dir)
        yield assertTrue(rows == List(CountryRow("Afghanistan", "AF"), CountryRow("Albania", "AL")))
      },
      test("toCommand builds a valid CreateCountryCommand from a well-formed row") {
        for command <- CountryCsvParser.toCommand(CountryRow("Spain", "ES"))
        yield assertTrue(command == CreateCountryCommand(CountryCode("ES"), "Spain"))
      },
      test("toCommand fails with InvalidCountryCode when the code is the wrong length") {
        for error <- CountryCsvParser.toCommand(CountryRow("Nowhereland", "XYZ")).flip
        yield assertTrue(error match
          case DomainError.InvalidCountryCode(errors) => errors.size == 1
          case _                                      => false)
      }
    )
