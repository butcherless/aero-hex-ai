package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airline.*
import dev.cmartin.aerohex.domain.country.{CountryCode, FindCountryUseCase}
import zio.*
import zio.nio.file.Path

object AirlineSync:

  private def keyOf(entry: (Airline, CountryCode)): AirlineIcaoCode = entry._1.icao

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
    * `Airline`'s case class itself has no `countryCode` field — the
    * relationship is resolved separately (`AirlineRepository.save`/`.update`'s
    * extra param) — so `EntitySync.reconcile`'s `==` diff runs over
    * `(Airline, CountryCode)` pairs instead of the bare entity, letting a
    * source row whose *only* change is its country still be detected as needing
    * an update. The existing side of that pair comes from
    * `FindAirlineUseCase.findAllUnboundedWithCountry`, a single joined query,
    * not one per-row lookup.
    *
    * OpenFlights occasionally lists two distinct airlines under the same ICAO
    * code (§9 of docs/todo/master-data/analysis.md) — deduplicated here, before
    * `EntitySync.reconcile` ever sees the source list, by keeping only the
    * first row per ICAO and logging+counting the rest as skipped, the same
    * tolerance every other unresolvable row gets.
    */
  def sync(file: Path): ZIO[
    CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase & FindAirlineUseCase & FindCountryUseCase,
    Throwable,
    SyncReport
  ] =
    for
      findCountryUseCase <- ZIO.service[FindCountryUseCase]
      countries          <- findCountryUseCase.findAllUnbounded
      countryNameToCode   = countries.map(c => c.name -> c.code).toMap
      rows               <- AirlineCsvParser.parse(file)
      validated          <- ZIO.foreach(rows)(row => AirlineCsvParser.toCommand(row, countryNameToCode).either)
      _                  <- ZIO.foreachDiscard(validated.collect { case Left(error) => error })(error =>
                              ZIO.logWarning(s"Skipped invalid Airline row: $error")
                            )
      commands            = validated.collect { case Right(command) => command }
      commandsByIcao      = commands.groupBy(_.icao)
      duplicateIcaoCount  = commands.size - commandsByIcao.size
      _                  <- ZIO.foreachDiscard(commandsByIcao.values.filter(_.size > 1)) { dupes =>
                              ZIO.logWarning(
                                s"Skipping ${dupes.size - 1} duplicate Airline row(s) for ICAO ${dupes.head.icao} " +
                                  s"(source names: ${dupes.map(_.name).mkString(", ")}) — keeping first occurrence"
                              )
                            }
      dedupedCommands     = commandsByIcao.values.map(_.head).toList
      airlines            = dedupedCommands.map(c => (Airline(c.icao, c.name, c.alias, c.callsign), c.countryCode))
      createUseCase      <- ZIO.service[CreateAirlineUseCase]
      updateUseCase      <- ZIO.service[UpdateAirlineUseCase]
      deleteUseCase      <- ZIO.service[DeleteAirlineUseCase]
      findAirlineUseCase <- ZIO.service[FindAirlineUseCase]
      existing           <-
        EntitySync.loadExisting(
          findAirlineUseCase.findAllUnboundedWithCountry.orDieWith(e => new RuntimeException(e.toString)),
          keyOf
        )
      plan                = EntitySync.reconcile(airlines, existing, keyOf)
      report             <- EntitySync.apply(
                              plan,
                              { case (airline, countryCode) =>
                                createUseCase
                                  .create(CreateAirlineCommand(
                                    airline.icao,
                                    airline.name,
                                    airline.alias,
                                    airline.callsign,
                                    countryCode
                                  ))
                                  .unit
                              },
                              { case (airline, countryCode) =>
                                updateUseCase
                                  .update(UpdateAirlineCommand(
                                    airline.icao,
                                    airline.name,
                                    airline.alias,
                                    airline.callsign,
                                    countryCode
                                  ))
                                  .unit
                              },
                              deleteUseCase.delete
                            )
    yield report.copy(skippedInvalid = validated.count(_.isLeft) + duplicateIcaoCount)
