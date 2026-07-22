package dev.cmartin.aerohex.infrastructure.masterdata

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
      }
    )
