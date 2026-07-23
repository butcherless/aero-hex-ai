package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.application.airline.{
  CreateAirlineService,
  DeleteAirlineService,
  FindAirlineService,
  UpdateAirlineService
}
import dev.cmartin.aerohex.application.airport.{
  CreateAirportService,
  DeleteAirportService,
  FindAirportService,
  UpdateAirportService
}
import dev.cmartin.aerohex.application.country.{
  CreateCountryService,
  DeleteCountryService,
  FindCountryService,
  UpdateCountryService
}
import dev.cmartin.aerohex.domain.airline.{
  AirlineRepository,
  CreateAirlineUseCase,
  DeleteAirlineUseCase,
  FindAirlineUseCase,
  UpdateAirlineUseCase
}
import dev.cmartin.aerohex.domain.airport.{
  AirportRepository,
  CreateAirportUseCase,
  DeleteAirportUseCase,
  FindAirportUseCase,
  UpdateAirportUseCase
}
import dev.cmartin.aerohex.domain.country.{
  CountryRepository,
  CreateCountryUseCase,
  DeleteCountryUseCase,
  FindCountryUseCase,
  UpdateCountryUseCase
}
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportRepository
import dev.cmartin.aerohex.infrastructure.persistence.quill.config.QuillDataSourceLayer
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryRepository
import zio.*
import zio.http.Client
import zio.logging.backend.SLF4J
import zio.nio.file.Path

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val tempDirPrefix = "master-data-sync-"
  private val countryUrl    = "https://datahub.io/core/country-list/_r/-/data.csv"
  private val airportUrl    = "https://ourairports.com/data/airports.csv"
  private val airlineUrl    = "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airlines.dat"

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val countryUseCasesLayer
      : TaskLayer[CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & FindCountryUseCase] =
    (countryRepoLayer >>> CreateCountryService.layer) ++
      (countryRepoLayer >>> UpdateCountryService.layer) ++
      (countryRepoLayer >>> DeleteCountryService.layer) ++
      (countryRepoLayer >>> FindCountryService.layer)

  private val airportRepoLayer: TaskLayer[AirportRepository] =
    QuillDataSourceLayer.live >>> QuillAirportRepository.layer

  private val airportUseCasesLayer
      : TaskLayer[CreateAirportUseCase & UpdateAirportUseCase & DeleteAirportUseCase & FindAirportUseCase] =
    (airportRepoLayer >>> CreateAirportService.layer) ++
      (airportRepoLayer >>> UpdateAirportService.layer) ++
      (airportRepoLayer >>> DeleteAirportService.layer) ++
      (airportRepoLayer >>> FindAirportService.layer)

  private val airlineRepoLayer: TaskLayer[AirlineRepository] =
    QuillDataSourceLayer.live >>> QuillAirlineRepository.layer

  private val airlineUseCasesLayer
      : TaskLayer[CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase & FindAirlineUseCase] =
    (airlineRepoLayer >>> CreateAirlineService.layer) ++
      (airlineRepoLayer >>> UpdateAirlineService.layer) ++
      (airlineRepoLayer >>> DeleteAirlineService.layer) ++
      (airlineRepoLayer >>> FindAirlineService.layer)

  private def release(dir: Path): UIO[Unit] =
    (TempDirectory.delete(dir) *> ZIO.logInfo(s"Deleted temporary directory: $dir")).ignoreLogged

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.acquireRelease(TempDirectory.create(tempDirPrefix))(release).flatMap { dir =>
      for
        _             <- ZIO.logInfo(s"Created temporary directory: $dir")
        countryDest    = dir / "countries.csv"
        _             <- HttpDownloader.download(countryUrl, countryDest).provide(Client.default)
        countryReport <- CountrySync.sync(countryDest).provide(countryUseCasesLayer)
        _             <- countryReport.log()
        airportDest    = dir / "airports.csv"
        _             <- HttpDownloader.download(airportUrl, airportDest).provide(Client.default)
        airportReport <- AirportSync.sync(airportDest).provide(airportUseCasesLayer)
        _             <- airportReport.log()
        airlineDest    = dir / "airlines.dat"
        _             <- HttpDownloader.download(airlineUrl, airlineDest).provide(Client.default)
        airlineReport <- AirlineSync.sync(airlineDest).provide(airlineUseCasesLayer ++ countryUseCasesLayer)
        _             <- airlineReport.log()
      yield ()
    }
