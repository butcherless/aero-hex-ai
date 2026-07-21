package dev.cmartin.aerohex.infrastructure.masterdata

import zio.*
import zio.logging.backend.SLF4J
import zio.nio.file.Path

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val tempDirPrefix = "master-data-sync-"

  private def release(dir: Path): UIO[Unit] =
    (TempDirectory.delete(dir) *> ZIO.logInfo(s"Deleted temporary directory: $dir")).ignoreLogged

  override def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    ZIO.acquireRelease(TempDirectory.create(tempDirPrefix))(release).flatMap { dir =>
      ZIO.logInfo(s"Created temporary directory: $dir") *>
        ZIO.logInfo(
          "master-data-sync scaffold run complete — download/parse/reconcile pipeline lands in a later increment."
        )
    }
