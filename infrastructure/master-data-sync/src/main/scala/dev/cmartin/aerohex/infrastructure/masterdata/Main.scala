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
import javax.sql.DataSource
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

  // Every entity wires its Quill repository the same way (QuillDataSourceLayer.live >>> the
  // repo's own .layer) and then its four use-case services the same way (repo layer >>> each
  // service's own .layer, ++-combined) — the two helpers below capture that shape once, generic
  // over the repo/use-case types, so each entity below is just a one-line call.
  private def repoLayer[Repo](quillLayer: URLayer[DataSource, Repo]): TaskLayer[Repo] =
    QuillDataSourceLayer.live >>> quillLayer

  private def useCasesLayer[Repo, Create, Update: Tag, Delete: Tag, Find: Tag](
      repo: TaskLayer[Repo]
  )(
      create: URLayer[Repo, Create],
      update: URLayer[Repo, Update],
      delete: URLayer[Repo, Delete],
      find: URLayer[Repo, Find]
  ): TaskLayer[Create & Update & Delete & Find] =
    (repo >>> create) ++ (repo >>> update) ++ (repo >>> delete) ++ (repo >>> find)

  private val countryRepoLayer: TaskLayer[CountryRepository] = repoLayer(QuillCountryRepository.layer)

  private val countryUseCasesLayer
      : TaskLayer[CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & FindCountryUseCase] =
    useCasesLayer(countryRepoLayer)(
      CreateCountryService.layer,
      UpdateCountryService.layer,
      DeleteCountryService.layer,
      FindCountryService.layer
    )

  private val airportRepoLayer: TaskLayer[AirportRepository] = repoLayer(QuillAirportRepository.layer)

  private val airportUseCasesLayer
      : TaskLayer[CreateAirportUseCase & UpdateAirportUseCase & DeleteAirportUseCase & FindAirportUseCase] =
    useCasesLayer(airportRepoLayer)(
      CreateAirportService.layer,
      UpdateAirportService.layer,
      DeleteAirportService.layer,
      FindAirportService.layer
    )

  private val airlineRepoLayer: TaskLayer[AirlineRepository] = repoLayer(QuillAirlineRepository.layer)

  private val airlineUseCasesLayer
      : TaskLayer[CreateAirlineUseCase & UpdateAirlineUseCase & DeleteAirlineUseCase & FindAirlineUseCase] =
    useCasesLayer(airlineRepoLayer)(
      CreateAirlineService.layer,
      UpdateAirlineService.layer,
      DeleteAirlineService.layer,
      FindAirlineService.layer
    )

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
