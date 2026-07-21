package dev.cmartin.aerohex.infrastructure.masterdata

import zio.nio.file.Files
import zio.test.*

object TempDirectorySpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment, Any] =
    suite("TempDirectory")(
      test("create makes a real, empty directory") {
        for
          dir    <- TempDirectory.create("temp-directory-spec-")
          exists <- Files.isDirectory(dir)
          _      <- TempDirectory.delete(dir)
        yield assertTrue(exists)
      },
      test("delete removes the directory, including a file inside it") {
        for
          dir        <- TempDirectory.create("temp-directory-spec-")
          _          <- Files.createFile(dir / "sample.txt")
          _          <- TempDirectory.delete(dir)
          stillThere <- Files.exists(dir)
        yield assertTrue(!stillThere)
      }
    )
