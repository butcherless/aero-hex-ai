package dev.cmartin.aerohex.infrastructure.masterdata

import com.github.tototoshi.csv.CSVReader
import dev.cmartin.aerohex.domain.airline.{AirlineIcaoCode, CreateAirlineCommand}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.*
import zio.nio.file.Path

final case class AirlineRow(
    icao: String,
    name: String,
    alias: Option[String],
    callsign: Option[String],
    countryName: String
)

object AirlineCsvParser:

  private val icaoShape = "^[A-Za-z]{3}$".r

  // OpenFlights country names that don't match this project's ISO-derived `countries.name` values
  // verbatim — not exhaustive, just the common cases found during live verification (§9 of
  // docs/todo/master-data/analysis.md). Anything still unmapped falls through to the same
  // log+skip tolerance every other unresolvable row gets.
  private val countryNameAliases: Map[String, String] = Map(
    "United States"                         -> "United States of America (the)",
    "United Kingdom"                        -> "United Kingdom of Great Britain and Northern Ireland (the)",
    "Russia"                                -> "Russian Federation (the)",
    "South Korea"                           -> "Korea (the Republic of)",
    "Democratic People's Republic of Korea" -> "Korea (the Democratic People's Republic of)",
    "Turkey"                                -> "Türkiye",
    "Czech Republic"                        -> "Czechia",
    "Ivory Coast"                           -> "Côte d'Ivoire",
    "Vietnam"                               -> "Viet Nam",
    "Iran"                                  -> "Iran (Islamic Republic of)",
    "Syria"                                 -> "Syrian Arab Republic (the)",
    "Syrian Arab Republic"                  -> "Syrian Arab Republic (the)",
    "Taiwan"                                -> "Taiwan (Province of China)",
    "United Arab Emirates"                  -> "United Arab Emirates (the)",
    "Philippines"                           -> "Philippines (the)",
    "Tanzania"                              -> "Tanzania, the United Republic of",
    "Bahamas"                               -> "Bahamas (The)",
    "Bolivia"                               -> "Bolivia (Plurinational State of)",
    "Brunei"                                -> "Brunei Darussalam",
    "Cape Verde"                            -> "Cabo Verde",
    "Cayman Islands"                        -> "Cayman Islands (the)",
    "Comoros"                               -> "Comoros (the)",
    "Cook Islands"                          -> "Cook Islands (the)",
    "Dominican Republic"                    -> "Dominican Republic (the)",
    "Faroe Islands"                         -> "Faroe Islands (the)",
    "Hong Kong SAR of China"                -> "Hong Kong",
    "Lao Peoples Democratic Republic"       -> "Lao People's Democratic Republic (the)",
    "Macedonia"                             -> "North Macedonia",
    "Marshall Islands"                      -> "Marshall Islands (the)",
    "Moldova"                               -> "Moldova (the Republic of)",
    "Netherlands"                           -> "Netherlands (Kingdom of the)",
    "Niger"                                 -> "Niger (the)",
    "Sudan"                                 -> "Sudan (the)",
    "Venezuela"                             -> "Venezuela (Bolivarian Republic of)",
    "Congo (Kinshasa)"                      -> "Congo (the Democratic Republic of the)",
    "Republic of the Congo"                 -> "Congo (the)"
  )

  def parse(file: Path): Task[List[AirlineRow]] =
    for
      allRows <- ZIO.attempt(CSVReader.open(file.toFile).all())
      rows    <- ZIO.foreach(allRows.filter(row => row.lift(7).contains("Y")))(parseRow)
    yield rows.flatten

  private def parseRow(row: List[String]): UIO[Option[AirlineRow]] =
    val name     = row(1)
    val icao     = row(4)
    val alias    = optionalField(row(2))
    val callsign = optionalField(row(5))
    val country  = row(6)
    if !icaoShape.matches(icao) then
      ZIO.logWarning(s"Skipping Airline row with no valid ICAO code: $icao / $name").as(None)
    else
      ZIO.succeed(Some(AirlineRow(icao, name, alias, callsign, country)))

  private def optionalField(value: String): Option[String] =
    if value.isEmpty || value == "\\N" then None else Some(value)

  def toCommand(row: AirlineRow, countryNameToCode: Map[String, CountryCode]): IO[DomainError, CreateAirlineCommand] =
    for
      icao        <- ZIO.fromEither(
                       AirlineIcaoCode
                         .validateAll(row.icao)
                         .toEitherWith(errs => DomainError.InvalidAirlineIcaoCode(errs.toChunk.toList))
                     )
      resolvedName = countryNameAliases.getOrElse(row.countryName, row.countryName)
      countryCode <-
        ZIO.fromOption(countryNameToCode.get(resolvedName)).orElseFail(DomainError.CountryNotFound(row.countryName))
    yield CreateAirlineCommand(icao, row.name, row.alias, row.callsign, countryCode)
