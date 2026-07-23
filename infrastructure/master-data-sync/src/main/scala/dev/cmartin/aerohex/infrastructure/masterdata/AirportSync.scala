package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.CountryCode
import zio.*
import zio.nio.file.Path

object AirportSync:

  private def keyOf(entry: (Airport, CountryCode)): IataCode = entry._1.iataCode

  /** Downloads-having-already-happened entry point: parses the given
    * OurAirports CSV file (already filtered to `large_airport`/`medium_airport`
    * rows with a valid IATA/ICAO shape, §9), validates each row the same way
    * the HTTP create path does, then reconciles the valid rows against whatever
    * is currently stored, calling the real Create/Update/Delete use cases for
    * every row that needs one. A row that fails parsing or validation is logged
    * and skipped, not allowed to abort the sync.
    *
    * `Airport`'s case class itself has no `countryCode` field — the
    * relationship is resolved separately (`AirportRepository.save`/`.update`'s
    * extra param) — so `EntitySync.reconcile`'s `==` diff runs over
    * `(Airport, CountryCode)` pairs instead of the bare entity, letting a
    * source row whose *only* change is its `iso_country` still be detected as
    * needing an update. The existing side of that pair comes from
    * `FindAirportUseCase.findAllUnboundedWithCountry`, a single joined query,
    * not one `findCountryByIata` call per existing row.
    */
  def sync(file: Path): ZIO[
    CreateAirportUseCase & UpdateAirportUseCase & DeleteAirportUseCase & FindAirportUseCase,
    Throwable,
    SyncReport
  ] =
    for
      rows          <- AirportCsvParser.parse(file)
      validated     <- ZIO.foreach(rows)(row => AirportCsvParser.toCommand(row).either)
      _             <- ZIO.foreachDiscard(validated.collect { case Left(error) => error })(error =>
                         ZIO.logWarning(s"Skipped invalid Airport row: $error")
                       )
      commands       = validated.collect { case Right(command) => command }
      airports       = commands.map(c => (Airport(c.iataCode, c.icaoCode, c.name, c.city), c.countryCode))
      createUseCase <- ZIO.service[CreateAirportUseCase]
      updateUseCase <- ZIO.service[UpdateAirportUseCase]
      deleteUseCase <- ZIO.service[DeleteAirportUseCase]
      findUseCase   <- ZIO.service[FindAirportUseCase]
      existing      <-
        EntitySync.loadExisting(
          findUseCase.findAllUnboundedWithCountry.orDieWith(e => new RuntimeException(e.toString)),
          keyOf
        )
      plan           = EntitySync.reconcile(airports, existing, keyOf)
      report        <- EntitySync.apply(
                         plan,
                         { case (airport, countryCode) =>
                           createUseCase
                             .create(
                               CreateAirportCommand(
                                 airport.iataCode,
                                 airport.icaoCode,
                                 airport.name,
                                 airport.city,
                                 countryCode
                               )
                             )
                             .unit
                         },
                         { case (airport, countryCode) =>
                           updateUseCase
                             .update(
                               UpdateAirportCommand(
                                 airport.iataCode,
                                 airport.icaoCode,
                                 airport.name,
                                 airport.city,
                                 countryCode
                               )
                             )
                             .unit
                         },
                         deleteUseCase.delete
                       )
    yield report.copy(skippedInvalid = validated.count(_.isLeft))
