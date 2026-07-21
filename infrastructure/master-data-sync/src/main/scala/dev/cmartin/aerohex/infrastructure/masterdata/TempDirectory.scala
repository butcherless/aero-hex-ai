package dev.cmartin.aerohex.infrastructure.masterdata

import java.io.IOException
import zio.*
import zio.nio.file.{Files, Path}

object TempDirectory:

  def create(prefix: String): IO[IOException, Path] =
    Files.createTempDirectory(prefix = Some(prefix), fileAttributes = Nil)

  def delete(dir: Path): IO[IOException, Unit] =
    Files.deleteRecursive(dir).unit
