package dev.cmartin.aerohex.infrastructure.masterdata

import dev.cmartin.aerohex.domain.airport.{Airport, FindAirportUseCase, IataCode}
import dev.cmartin.aerohex.domain.route.*
import zio.*
import zio.nio.file.Path

object RouteSync:

  private def keyOf(route: Route): (IataCode, IataCode) = (route.origin, route.destination)

  /** Downloads-having-already-happened entry point: parses the given
    * OpenFlights `routes.dat` file (direct segments only — multi-leg rows are
    * filtered out by `RouteCsvParser`), resolves each row's source/destination
    * IATA codes against the full local `Airport` list (one bulk
    * `findAllUnbounded` call, not a per-row lookup), computes `distanceKm` via
    * `DistanceCalculator.haversineKm` from the two endpoint airports' stored
    * coordinates, then reconciles the result against whatever `Route` rows are
    * currently stored, calling the real Create/Update/Delete use cases for
    * every row that needs one. A row referencing an unknown airport, or whose
    * origin/destination airport are identical, is logged and skipped, not
    * failed hard.
    *
    * OpenFlights lists the same origin/destination pair once per operating
    * airline — deduplicated here (first occurrence kept) before
    * `EntitySync.reconcile` ever sees the source list, the same tolerance
    * `AirlineSync` applies to duplicate ICAOs. Which airline flies a route
    * (`route_airlines`) is out of scope here — see
    * plans/masterdata/route-sync.md.
    */
  def sync(file: Path): ZIO[
    CreateRouteUseCase & UpdateRouteUseCase & DeleteRouteUseCase & FindRouteUseCase & FindAirportUseCase,
    Throwable,
    SyncReport
  ] =
    for
      findAirportUseCase <- ZIO.service[FindAirportUseCase]
      airports           <- findAirportUseCase.findAllUnbounded.orDieWith(e => new RuntimeException(e.toString))
      airportsByIata      = airports.map(a => a.iataCode -> a).toMap
      rows               <- RouteCsvParser.parse(file)
      rowsByKey           = rows.groupBy(r => (r.sourceIata, r.destinationIata))
      duplicateCount      = rows.size - rowsByKey.size
      dedupedRows         = rowsByKey.values.map(_.head).toList
      resolved           <- ZIO.foreach(dedupedRows)(resolveRoute(_, airportsByIata))
      skippedUnknown      = resolved.count(_.isEmpty)
      routes              = resolved.flatten
      createUseCase      <- ZIO.service[CreateRouteUseCase]
      updateUseCase      <- ZIO.service[UpdateRouteUseCase]
      deleteUseCase      <- ZIO.service[DeleteRouteUseCase]
      findRouteUseCase   <- ZIO.service[FindRouteUseCase]
      existing           <-
        EntitySync.loadExisting(
          findRouteUseCase.findAllUnbounded.orDieWith(e => new RuntimeException(e.toString)),
          keyOf
        )
      plan                = EntitySync.reconcile(routes, existing, keyOf)
      report             <- EntitySync.apply(
                              plan,
                              route =>
                                createUseCase
                                  .create(
                                    CreateRouteCommand(route.origin.value, route.destination.value, route.distanceKm)
                                  )
                                  .unit,
                              route =>
                                updateUseCase
                                  .update(
                                    UpdateRouteCommand(route.origin.value, route.destination.value, route.distanceKm)
                                  )
                                  .unit,
                              { case (origin, destination) => deleteUseCase.delete(origin, destination) }
                            )
    yield report.copy(skippedInvalid = skippedUnknown + duplicateCount)

  private def resolveRoute(row: RouteRow, airportsByIata: Map[IataCode, Airport]): UIO[Option[Route]] =
    val origin      = airportsByIata.get(IataCode.unsafeMake(row.sourceIata))
    val destination = airportsByIata.get(IataCode.unsafeMake(row.destinationIata))
    (origin, destination) match
      case (Some(o), Some(d)) if o.iataCode == d.iataCode =>
        ZIO.logWarning(s"Skipping Route row with identical origin and destination: ${o.iataCode.value}").as(None)
      case (Some(o), Some(d))                             =>
        val distanceKm = DistanceCalculator.haversineKm(o.latitude, o.longitude, d.latitude, d.longitude).round.toInt
        ZIO.succeed(Some(Route(o.iataCode, d.iataCode, distanceKm)))
      case _                                              =>
        ZIO
          .logWarning(s"Skipping Route row with unknown airport(s): ${row.sourceIata} -> ${row.destinationIata}")
          .as(None)
