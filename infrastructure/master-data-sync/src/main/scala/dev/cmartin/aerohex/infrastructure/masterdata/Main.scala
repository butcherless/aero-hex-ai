package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.application.country.{CreateCountryService, DeleteCountryService, FindCountryService, UpdateCountryService}
import dev.cmartin.aerohex.domain.country.{CountryRepository, CreateCountryUseCase, DeleteCountryUseCase, FindCountryUseCase, UpdateCountryUseCase}
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

  private val countryRepoLayer: TaskLayer[CountryRepository] =
    QuillDataSourceLayer.live >>> QuillCountryRepository.layer

  private val countryUseCasesLayer
    : TaskLayer[CreateCountryUseCase & UpdateCountryUseCase & DeleteCountryUseCase & FindCountryUseCase] =
    (countryRepoLayer >>> CreateCountryService.layer) ++
      (countryRepoLayer >>> UpdateCountryService.layer) ++
      (countryRepoLayer >>> DeleteCountryService.layer) ++
      (countryRepoLayer >>> FindCountryService.layer)

  private def release(dir: Path): UIO[Unit] =
    (TempDirectory.delete(dir) *> ZIO.logInfo(s"Deleted temporary directory: $dir")).ignoreLogged

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.acquireRelease(TempDirectory.create(tempDirPrefix))(release).flatMap { dir =>
      for
        _      <- ZIO.logInfo(s"Created temporary directory: $dir")
        dest    = dir / "countries.csv"
        _      <- HttpDownloader.download(countryUrl, dest).provide(Client.default)
        report <- CountrySync.sync(dest).provide(countryUseCasesLayer)
        _      <- report.log()
      yield ()
    }
