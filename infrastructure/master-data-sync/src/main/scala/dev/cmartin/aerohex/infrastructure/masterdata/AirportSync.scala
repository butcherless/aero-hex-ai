package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.*
import dev.cmartin.aerohex.domain.country.CountryCode
import zio.*
import zio.nio.file.Path

object AirportSync:

  private def keyOf(airport: Airport): IataCode = airport.iataCode

  /** Downloads-having-already-happened entry point: parses the given
    * OurAirports CSV file (already filtered to `large_airport`/`medium_airport`
    * rows with a valid IATA/ICAO shape, §9), validates each row the same way
    * the HTTP create path does, then reconciles the valid rows against whatever
    * is currently stored, calling the real Create/Update/Delete use cases for
    * every row that needs one. A row that fails parsing or validation is logged
    * and skipped, not allowed to abort the sync.
    *
    * Known limitation: `Airport`'s case class (and therefore
    * `EntitySync.reconcile`'s `==` diff) has no `countryCode` field — the
    * relationship is resolved separately (`AirportRepository.save`/ `.update`'s
    * extra param) — so a source row whose `iso_country` changes while
    * iata/icao/name/city stay identical won't be detected as needing an update.
    * `countryCode` for a create/update call itself is looked up from
    * `countryCodeByIata` below, built directly from the parsed source rows, so
    * it's always correct on create — only "did the country change" diff
    * detection is affected.
    */
  def sync(file: Path): ZIO[
    CreateAirportUseCase & UpdateAirportUseCase & DeleteAirportUseCase & FindAirportUseCase,
    Throwable,
    SyncReport
  ] =
    for
      rows                                         <- AirportCsvParser.parse(file)
      validated                                    <- ZIO.foreach(rows)(row => AirportCsvParser.toCommand(row).either)
      _                                            <- ZIO.foreachDiscard(validated.collect { case Left(error) => error })(error =>
                                                        ZIO.logWarning(s"Skipped invalid Airport row: $error")
                                                      )
      commands                                      = validated.collect { case Right(command) => command }
      airports                                      = commands.map(c => Airport(c.iataCode, c.icaoCode, c.name, c.city))
      countryCodeByIata: Map[IataCode, CountryCode] = commands.map(c => c.iataCode -> c.countryCode).toMap
      createUseCase                                <- ZIO.service[CreateAirportUseCase]
      updateUseCase                                <- ZIO.service[UpdateAirportUseCase]
      deleteUseCase                                <- ZIO.service[DeleteAirportUseCase]
      findUseCase                                  <- ZIO.service[FindAirportUseCase]
      existing                                     <-
        EntitySync.loadExisting(findUseCase.findAllUnbounded.orDieWith(e => new RuntimeException(e.toString)), keyOf)
      plan                                          = EntitySync.reconcile(airports, existing, keyOf)
      report                                       <- EntitySync.apply(
                                                        plan,
                                                        airport =>
                                                          createUseCase
                                                            .create(
                                                              CreateAirportCommand(
                                                                airport.iataCode,
                                                                airport.icaoCode,
                                                                airport.name,
                                                                airport.city,
                                                                countryCodeByIata(airport.iataCode)
                                                              )
                                                            )
                                                            .unit,
                                                        airport =>
                                                          updateUseCase
                                                            .update(
                                                              UpdateAirportCommand(
                                                                airport.iataCode,
                                                                airport.icaoCode,
                                                                airport.name,
                                                                airport.city,
                                                                countryCodeByIata(airport.iataCode)
                                                              )
                                                            )
                                                            .unit,
                                                        deleteUseCase.delete
                                                      )
    yield report.copy(skippedInvalid = validated.count(_.isLeft))
