package dev.cmartin.aerohex.infrastructure.masterdata

import com.github.tototoshi.csv.CSVReader
import zio.*
import zio.nio.file.Path

// One (sourceIata, destinationIata) pair from a direct-only routes.dat row — airport identity is
// resolved (and the airport-existence check performed) later, in RouteSync, against the locally
// stored Airport list, not here.
final case class RouteRow(sourceIata: String, destinationIata: String)

object RouteCsvParser:

  // routes.dat has no header row; columns are positional: Airline, AirlineID, SourceAirport,
  // SourceAirportID, DestinationAirport, DestinationAirportID, Codeshare, Stops, Equipment.
  def parse(file: Path): Task[List[RouteRow]] =
    for
      allRows <- ZIO.attempt(CSVReader.open(file.toFile).all())
      rows    <- ZIO.foreach(allRows)(parseRow)
    yield rows.flatten

  // Multi-leg rows (Stops > 0) are silently filtered — Route models direct segments only, the
  // same "not relevant to this entity" tolerance AirportCsvParser applies to non-large/medium
  // airport types.
  private def parseRow(row: List[String]): UIO[Option[RouteRow]] =
    val source      = row.lift(2).getOrElse("")
    val destination = row.lift(4).getOrElse("")
    val stops       = row.lift(7).flatMap(_.toIntOption).getOrElse(-1)
    if stops != 0 then ZIO.succeed(None)
    else if source.isEmpty || destination.isEmpty then
      ZIO.logWarning(s"Skipping Route row with a blank source/destination airport: $row").as(None)
    else ZIO.succeed(Some(RouteRow(source, destination)))
