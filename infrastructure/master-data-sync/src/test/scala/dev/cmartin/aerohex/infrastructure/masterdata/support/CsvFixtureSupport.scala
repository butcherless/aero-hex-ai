package dev.cmartin.aerohex.infrastructure.masterdata.support

import dev.cmartin.aerohex.infrastructure.masterdata.TempDirectory
import java.io.IOException
import zio.IO
import zio.nio.file.{Files, Path}

final case class CsvFixture(dir: Path, file: Path)

object CsvFixtureSupport:

  // Every *SyncSpec writes its source rows to a temp file under its own prefix/filename before
  // running the sync against it — this is the shared "write, then hand back dir+file" plumbing.
  def writeCsv(prefix: String, fileName: String, lines: List[String]): IO[IOException, CsvFixture] =
    for
      dir <- TempDirectory.create(prefix)
      file = dir / fileName
      _   <- Files.writeLines(file, lines)
    yield CsvFixture(dir, file)
