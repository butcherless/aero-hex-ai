package dev.cmartin.aerohex.infrastructure.masterdata

import com.github.tototoshi.csv.CSVReader
import dev.cmartin.aerohex.domain.airport.{AirportIcaoCode, CreateAirportCommand, IataCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import zio.*
import zio.nio.file.Path

final case class AirportRow(iataCode: String, icaoCode: String, name: String, city: String, countryCode: String)

object AirportCsvParser:

  // §9: small_airport/heliport/closed_airport/etc. rarely carry a real IATA code.
  private val includedTypes = Set("large_airport", "medium_airport")
  private val icaoShape     = "^[A-Za-z]{4}$".r

  def parse(file: Path): Task[List[AirportRow]] =
    for
      allRows <- ZIO.attempt(CSVReader.open(file.toFile).allWithHeaders())
      rows    <- ZIO.foreach(allRows.filter(row => includedTypes.contains(row("type"))))(parseRow)
    yield rows.flatten

  // OurAirports' own `icao_code` column holds the real ICAO code when present; `ident` is its row
  // identifier, which for airports without a dedicated icao_code falls back to a locally-generated
  // (non-ICAO-shaped) value. Preferring icao_code and falling back to ident only when it's blank
  // recovers real ICAO codes that live only in icao_code for some large/medium airports whose ident
  // is an internal id (e.g. "AE-0221").
  private def parseRow(row: Map[String, String]): UIO[Option[AirportRow]] =
    val iata = row("iata_code").trim
    val icao = row("icao_code").trim match
      case blank if blank.isEmpty => row("ident").trim
      case value                  => value
    if iata.isEmpty then
      ZIO.logWarning(s"Skipping Airport row with blank iata_code: ${row("name")}").as(None)
    else if !icaoShape.matches(icao) then
      ZIO.logWarning(s"Skipping Airport row with no valid ICAO code: $iata / ${row("name")}").as(None)
    else
      ZIO.succeed(Some(AirportRow(iata, icao, row("name"), row("municipality"), row("iso_country"))))

  def toCommand(row: AirportRow): IO[DomainError, CreateAirportCommand] =
    for
      iataCode <-
        ZIO.fromEither(
          IataCode.validateAll(row.iataCode).toEitherWith(errs => DomainError.InvalidIataCode(errs.toChunk.toList))
        )
      icaoCode <- ZIO.fromEither(
                    AirportIcaoCode
                      .validateAll(row.icaoCode)
                      .toEitherWith(errs => DomainError.InvalidAirportIcaoCode(errs.toChunk.toList))
                  )
    yield CreateAirportCommand(
      iataCode = iataCode,
      icaoCode = icaoCode,
      name = row.name,
      city = row.city,
      countryCode = CountryCode.unsafeMake(row.countryCode)
    )
