package dev.cmartin.aerohex.infrastructure.masterdata

import zio.*
import zio.http.*
import zio.nio.file.Path
import zio.stream.ZSink

object HttpDownloader:

  private val followRedirects =
    ZClientAspect.followRedirects(5)((response, message) => ZIO.logWarning(message).as(response))

  // Row/record counts are format-specific (CSV vs. .dat) and belong to whichever parser reads the
  // downloaded file next — HttpDownloader only ever sees an undifferentiated byte stream.
  private def humanReadableSize(bytes: Long): String =
    if bytes < 1024 then s"$bytes B"
    else if bytes < 1024 * 1024 then f"${bytes / 1024.0}%.1f KB"
    else f"${bytes / (1024.0 * 1024)}%.1f MB"

  def download(url: String, destFile: Path): ZIO[Client, Throwable, Path] =
    ZIO
      .scoped {
        for
          _            <- ZIO.logInfo(s"Downloading $url -> $destFile")
          response     <- Client.streaming(Request.get(url))
          _            <- ZIO
                            .fail(new RuntimeException(s"GET $url failed with status ${response.status}"))
                            .unless(response.status.isSuccess)
          bytesWritten <- response.body.asStream.run(ZSink.fromFile(destFile.toFile))
          _            <- ZIO.logInfo(
                            s"Downloaded ${humanReadableSize(bytesWritten)} ($bytesWritten bytes): $url -> $destFile"
                          )
        yield destFile
      }
      .updateService[Client](_ @@ followRedirects)
      .tapError(e => ZIO.logError(s"Download failed: $url -> $destFile — ${e.getMessage}"))
