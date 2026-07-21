package dev.cmartin.aerohex.infrastructure.masterdata

import java.nio.charset.StandardCharsets
import zio.*
import zio.http.*
import zio.nio.file.{Files, Path}
import zio.test.*

object HttpDownloaderSpec extends ZIOSpecDefault:

  private val countryCsv =
    "Name,Code\nAfghanistan,AF\n\"Bonaire, Sint Eustatius and Saba\",BQ\n"

  private val redirectTarget = URL.decode("/countries.csv").toOption.get

  private val routes = Routes(
    Method.GET / "countries.csv" -> handler(Response.text(countryCsv)),
    Method.GET / "redirect"      -> handler(Response.redirect(redirectTarget)),
    Method.GET / "missing"       -> handler(Response.notFound)
  )

  private def readBackAsString(file: Path): Task[String] =
    Files.readAllBytes(file).map(bytes => new String(bytes.toArray, StandardCharsets.UTF_8))

  // Routes are installed once, when the shared layer builds, rather than per test — reinstalling
  // the same routes on every test would register duplicates against the one shared server.
  private val portLayer: ZLayer[Any, Throwable, Int] =
    Server.defaultWithPort(0) >>> ZLayer.fromZIO(Server.install(routes))

  override def spec: Spec[TestEnvironment, Any] =
    suite("HttpDownloader")(
      test("downloads a direct 200 response to the destination file") {
        for
          port    <- ZIO.service[Int]
          tmpDir  <- TempDirectory.create("http-downloader-spec-")
          dest     = tmpDir / "countries.csv"
          _       <- HttpDownloader.download(s"http://localhost:$port/countries.csv", dest)
          content <- readBackAsString(dest)
          _       <- TempDirectory.delete(tmpDir)
        yield assertTrue(content == countryCsv)
      },
      test("follows a redirect to the final content") {
        for
          port    <- ZIO.service[Int]
          tmpDir  <- TempDirectory.create("http-downloader-spec-")
          dest     = tmpDir / "redirected.csv"
          _       <- HttpDownloader.download(s"http://localhost:$port/redirect", dest)
          content <- readBackAsString(dest)
          _       <- TempDirectory.delete(tmpDir)
        yield assertTrue(content == countryCsv)
      },
      test("fails on a non-2xx response") {
        for
          port   <- ZIO.service[Int]
          tmpDir <- TempDirectory.create("http-downloader-spec-")
          dest    = tmpDir / "missing.csv"
          exit   <- HttpDownloader.download(s"http://localhost:$port/missing", dest).exit
          _      <- TempDirectory.delete(tmpDir)
        yield assertTrue(exit.isFailure)
      }
    ).provideLayerShared(portLayer ++ Client.default)
