package dev.cmartin.aerohex.infrastructure.persistence.postgres.route

import dev.cmartin.aerohex.infrastructure.persistence.postgres.common.DoobieIdResolver
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.route.{Route, RouteId}
import dev.cmartin.aerohex.domain.route.RouteRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, URLayer, ZLayer}
import zio.interop.catz.*

import java.util.UUID

final class DoobieRouteRepository(protected val xa: Transactor[Task]) extends RouteRepository
    with DoobieIdResolver {

  private def resolveAirportId(iata: IataCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airports WHERE iata_code = ${iata.value}".query[Long],
      DomainError.AirportNotFound(iata.value)
    )

  private def resolveAirlineId(icao: IcaoCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airlines WHERE icao_code = ${icao.value}".query[Long],
      DomainError.AirlineNotFound(icao.value)
    )

  override def findById(id: RouteId): IO[DomainError, Option[Route]] =
    sql"""SELECT r.id, ao.iata_code, ad.iata_code, al.icao_code, r.distance_km
          FROM routes r
            JOIN airports ao ON r.origin_airport_id = ao.id
            JOIN airports ad ON r.destination_airport_id = ad.id
            JOIN airlines al ON r.airline_id = al.id
          WHERE r.id = ${id.value}"""
      .query[(UUID, String, String, String, Int)]
      .option
      .transact(xa)
      .map(_.map((i, o, d, a, dist) =>
        Route(RouteId(i), IataCode.unsafeMake(o), IataCode.unsafeMake(d), IcaoCode.unsafeMake(a), dist)
      ))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Route]] =
    sql"""SELECT r.id, ao.iata_code, ad.iata_code, al.icao_code, r.distance_km
          FROM routes r
            JOIN airports ao ON r.origin_airport_id = ao.id
            JOIN airports ad ON r.destination_airport_id = ad.id
            JOIN airlines al ON r.airline_id = al.id
          ORDER BY r.id LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(UUID, String, String, String, Int)]
      .to[List]
      .transact(xa)
      .map(_.map((i, o, d, a, dist) =>
        Route(RouteId(i), IataCode.unsafeMake(o), IataCode.unsafeMake(d), IcaoCode.unsafeMake(a), dist)
      ))
      .orDie

  override def save(route: Route): IO[DomainError, Route] =
    for {
      originId      <- resolveAirportId(route.origin)
      destinationId <- resolveAirportId(route.destination)
      airlineId     <- resolveAirlineId(route.airlineIcao)
      _             <- sql"""
             INSERT INTO routes (id, origin_airport_id, destination_airport_id, airline_id, distance_km)
             VALUES (${route.id.value}, $originId, $destinationId, $airlineId, ${route.distanceKm})
             ON CONFLICT (id) DO UPDATE
               SET distance_km = EXCLUDED.distance_km
           """.update.run
                         .transact(xa)
                         .orDie
    } yield route

  override def delete(id: RouteId): IO[DomainError, Unit] =
    sql"DELETE FROM routes WHERE id = ${id.value}"
      .update.run
      .transact(xa)
      .unit
      .orDie
}

object DoobieRouteRepository {
  val layer: URLayer[Transactor[Task], RouteRepository] =
    ZLayer.fromFunction(new DoobieRouteRepository(_))
}
