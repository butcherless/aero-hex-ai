package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.country.{CountryCode, FindCountryUseCase}
import zio.*
import zio.nio.file.Path

object AirlineSync:

  private def keyOf(airline: Airline): AirlineIcaoCode = airline.icao

  /** Downloads-having-already-happened entry point: parses the given
    * OpenFlights CSV file, resolves each row's free-text country name to a
    * `CountryCode` via a live-fetched `Country` list (plus a small alias table
    * for known name variants, `AirlineCsvParser.countryNameAliases`), validates
    * each row the same way the HTTP create path does, then reconciles the valid
    * rows against whatever is currently stored, calling the real
    * Create/Update/Delete use cases for every row that needs one. A row that
    * fails parsing, country resolution, or validation is logged and skipped,
    * not allowed to abort the sync.
    *
    * Known limitation, same as `AirportSync`: `Airline`'s case class has no
    * `countryCode` field — the relationship is resolved separately
    * (`AirlineRepository.save`/`.update`'s extra param) — so a source row whose
    * *only* change is its country won't be detected as needing an update.
    * `countryCode` for a create/update call itself is looked up from
    * `countryCodeByIcao` below, built directly from the parsed source, so it's
    * always correct on create — only "did the country change" diff detection is
    * affected.
    */
  def sync(file: Path): ZIO[
    CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase & FindAirlineUseCase & FindCountryUseCase,
    Throwable,
    SyncReport
  ] =
    for
      findCountryUseCase                                  <- ZIO.service[FindCountryUseCase]
      countries                                           <- findCountryUseCase.findAllUnbounded
      countryNameToCode                                    = countries.map(c => c.name -> c.code).toMap
      rows                                                <- AirlineCsvParser.parse(file)
      validated                                           <- ZIO.foreach(rows)(row => AirlineCsvParser.toCommand(row, countryNameToCode).either)
      _                                                   <- ZIO.foreachDiscard(validated.collect { case Left(error) => error })(error =>
                                                               ZIO.logWarning(s"Skipped invalid Airline row: $error")
                                                             )
      commands                                             = validated.collect { case Right(command) => command }
      airlines                                             = commands.map(c => Airline(c.icao, c.name, c.alias, c.callsign))
      countryCodeByIcao: Map[AirlineIcaoCode, CountryCode] = commands.map(c => c.icao -> c.countryCode).toMap
      createUseCase                                       <- ZIO.service[CreateAirlineUseCase]
      updateUseCase                                       <- ZIO.service[UpdateAirlineUseCase]
      deleteUseCase                                       <- ZIO.service[DeleteAirlineUseCase]
      findAirlineUseCase                                  <- ZIO.service[FindAirlineUseCase]
      existing                                            <-
        EntitySync.loadExisting(
          findAirlineUseCase.findAllUnbounded.orDieWith(e => new RuntimeException(e.toString)),
          keyOf
        )
      plan                                                 = EntitySync.reconcile(airlines, existing, keyOf)
      report                                              <- EntitySync.apply(
                                                               plan,
                                                               airline =>
                                                                 createUseCase
                                                                   .create(
                                                                     CreateAirlineCommand(
                                                                       airline.icao,
                                                                       airline.name,
                                                                       airline.alias,
                                                                       airline.callsign,
                                                                       countryCodeByIcao(airline.icao)
                                                                     )
                                                                   )
                                                                   .unit,
                                                               airline =>
                                                                 updateUseCase
                                                                   .update(
                                                                     UpdateAirlineCommand(
                                                                       airline.icao,
                                                                       airline.name,
                                                                       airline.alias,
                                                                       airline.callsign,
                                                                       countryCodeByIcao(airline.icao)
                                                                     )
                                                                   )
                                                                   .unit,
                                                               deleteUseCase.delete
                                                             )
    yield report.copy(skippedInvalid = validated.count(_.isLeft))
