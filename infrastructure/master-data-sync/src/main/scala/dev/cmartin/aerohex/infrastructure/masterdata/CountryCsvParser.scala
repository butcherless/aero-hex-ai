package dev.cmartin.aerohex.infrastructure.masterdata

import java.io.IOException
import zio.*
import zio.nio.file.{Files, Path}

final case class CountryRow(name: String, code: String)

object CountryCsvParser:

  private val rowPattern = """^(?:"([^"]+)"|([^,]+)),([A-Za-z]{2})$""".r

  def parse(file: Path): IO[IOException, List[CountryRow]] =
    for
      lines <- Files.readAllLines(file)
      rows  <- ZIO.foreach(lines.drop(1))(parseLine)
    yield rows.flatten

  private def parseLine(line: String): UIO[Option[CountryRow]] =
    line match
      case rowPattern(quotedName, plainName, code) =>
        ZIO.succeed(Some(CountryRow(Option(quotedName).getOrElse(plainName), code)))
      case _                                       =>
        ZIO.logWarning(s"Skipping malformed Country CSV line: $line").as(None)
