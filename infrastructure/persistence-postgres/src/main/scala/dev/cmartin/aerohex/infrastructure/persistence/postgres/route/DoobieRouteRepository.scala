package dev.cmartin.aerohex.infrastructure.persistence.postgres.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.Route
import dev.cmartin.aerohex.domain.route.RouteRepository
import dev.cmartin.aerohex.infrastructure.persistence.postgres.common.DoobieIdResolver
import dev.cmartin.aerohex.shared.Pagination
import doobie.Transactor
import doobie.implicits.*
import zio.interop.catz.*
import zio.{IO, Task, URLayer, ZLayer}

final class DoobieRouteRepository(protected val xa: Transactor[Task]) extends RouteRepository
    with DoobieIdResolver {

  private def resolveAirportId(iata: IataCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airports WHERE iata_code = ${iata.value}".query[Long],
      DomainError.AirportNotFound(iata.value)
    )

  override def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Option[Route]] =
    sql"""SELECT ao.iata_code, ad.iata_code, r.distance_km
          FROM routes r
            JOIN airports ao ON r.origin_airport_id = ao.id
            JOIN airports ad ON r.destination_airport_id = ad.id
          WHERE ao.iata_code = ${origin.value} AND ad.iata_code = ${destination.value}"""
      .query[(String, String, Int)]
      .option
      .transact(xa)
      .map(_.map((o, d, dist) => Route(IataCode.unsafeMake(o), IataCode.unsafeMake(d), dist)))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Route]] =
    sql"""SELECT ao.iata_code, ad.iata_code, r.distance_km
          FROM routes r
            JOIN airports ao ON r.origin_airport_id = ao.id
            JOIN airports ad ON r.destination_airport_id = ad.id
          ORDER BY ao.iata_code, ad.iata_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, Int)]
      .to[List]
      .transact(xa)
      .map(_.map((o, d, dist) => Route(IataCode.unsafeMake(o), IataCode.unsafeMake(d), dist)))
      .orDie

  override def save(route: Route): IO[DomainError, Route] =
    for {
      originId      <- resolveAirportId(route.origin)
      destinationId <- resolveAirportId(route.destination)
      _             <- sql"""
             INSERT INTO routes (origin_airport_id, destination_airport_id, distance_km)
             VALUES ($originId, $destinationId, ${route.distanceKm})
             ON CONFLICT (origin_airport_id, destination_airport_id) DO UPDATE
               SET distance_km = EXCLUDED.distance_km
           """.update.run
                         .transact(xa)
                         .orDie
    } yield route

  override def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit] =
    sql"""DELETE FROM routes r
          USING airports ao, airports ad
          WHERE r.origin_airport_id = ao.id AND r.destination_airport_id = ad.id
            AND ao.iata_code = ${origin.value} AND ad.iata_code = ${destination.value}"""
      .update.run
      .transact(xa)
      .unit
      .orDie
}

object DoobieRouteRepository {
  val layer: URLayer[Transactor[Task], RouteRepository] =
    ZLayer.fromFunction(new DoobieRouteRepository(_))
}
