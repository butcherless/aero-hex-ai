package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.country.*
import java.io.IOException
import zio.*
import zio.nio.file.Path

object CountrySync:

  private def keyOf(country: Country): CountryCode = country.code

  /** Downloads-having-already-happened entry point: parses the given Country CSV file, validates each
    * row the same way the HTTP create path does, then reconciles the valid rows against whatever is
    * currently stored, calling the real Create/Update/Delete use cases for every row that needs one.
    * A row that fails parsing or validation is logged and skipped, not allowed to abort the sync.
    */
  def sync(file: Path): ZIO[
    CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & FindCountryUseCase,
    IOException,
    SyncReport
  ] =
    for
      rows          <- CountryCsvParser.parse(file)
      validated     <- ZIO.foreach(rows)(row => CountryCsvParser.toCommand(row).either)
      _             <- ZIO.foreachDiscard(validated.collect { case Left(error) => error })(error =>
                         ZIO.logWarning(s"Skipped invalid Country row: $error")
                       )
      countries      = validated.collect { case Right(command) => Country(command.code, command.name) }
      createUseCase <- ZIO.service[CreateCountryUseCase]
      updateUseCase <- ZIO.service[UpdateCountryUseCase]
      deleteUseCase <- ZIO.service[DeleteCountryUseCase]
      findUseCase   <- ZIO.service[FindCountryUseCase]
      existing      <- EntitySync.loadExisting(findUseCase.findAllUnbounded, keyOf)
      plan           = EntitySync.reconcile(countries, existing, keyOf)
      report        <- EntitySync.apply(
                         plan,
                         country => createUseCase.create(CreateCountryCommand(country.code, country.name)).unit,
                         country => updateUseCase.update(UpdateCountryCommand(country.code, country.name)).unit,
                         deleteUseCase.delete
                       )
    yield report.copy(skippedInvalid = validated.count(_.isLeft))
