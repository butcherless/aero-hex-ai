package dev.cmartin.aerohex.infrastructure.masterdata

import zio.*
import zio.http.Client
import zio.logging.backend.SLF4J
import zio.nio.file.Path

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val tempDirPrefix = "master-data-sync-"
  private val countryUrl    = "https://datahub.io/core/country-list/_r/-/data.csv"

  private def release(dir: Path): UIO[Unit] =
    (TempDirectory.delete(dir) *> ZIO.logInfo(s"Deleted temporary directory: $dir")).ignoreLogged

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.acquireRelease(TempDirectory.create(tempDirPrefix))(release).flatMap { dir =>
      for
        _    <- ZIO.logInfo(s"Created temporary directory: $dir")
        dest  = dir / "countries.csv"
        _    <- HttpDownloader.download(countryUrl, dest).provide(Client.default)
        rows <- CountryCsvParser.parse(dest)
        _    <- ZIO.logInfo(
                  s"master-data-sync: parsed ${rows.size} Country rows — reconciliation lands in a later increment."
                )
      yield ()
    }
