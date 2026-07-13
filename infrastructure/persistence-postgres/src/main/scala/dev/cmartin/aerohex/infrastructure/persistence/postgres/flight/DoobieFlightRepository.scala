package dev.cmartin.aerohex.infrastructure.persistence.postgres.flight

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode, FlightRepository}
import dev.cmartin.aerohex.infrastructure.persistence.postgres.common.DoobieIdResolver
import dev.cmartin.aerohex.shared.Pagination
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import java.time.{LocalDate, LocalTime}
import zio.interop.catz.*
import zio.{IO, Task, URLayer, ZIO, ZLayer}

final class DoobieFlightRepository(protected val xa: Transactor[Task]) extends FlightRepository
    with DoobieIdResolver {

  private def resolveAirportId(iata: IataCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airports WHERE iata_code = ${iata.value}".query[Long],
      DomainError.AirportNotFound(iata.value)
    )

  private def resolveAirlineId(icao: AirlineIcaoCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airlines WHERE icao_code = ${icao.value}".query[Long],
      DomainError.AirlineNotFound(icao.value)
    )

  private def toFlight(
      code: String,
      alias: Option[String],
      schedDeparture: LocalTime,
      schedArrival: LocalTime,
      originIata: String,
      destinationIata: String,
      airlineIcao: String
  ): Flight =
    Flight(
      FlightCode.unsafeMake(code),
      alias,
      schedDeparture,
      schedArrival,
      IataCode.unsafeMake(originIata),
      IataCode.unsafeMake(destinationIata),
      AirlineIcaoCode.unsafeMake(airlineIcao)
    )

  override def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] =
    sql"""SELECT f.code, f.alias, f.sched_departure, f.sched_arrival, ao.iata_code, ad.iata_code, l.icao_code
          FROM flights f
            JOIN airports ao ON f.origin_airport_id = ao.id
            JOIN airports ad ON f.destination_airport_id = ad.id
            JOIN airlines l ON f.airline_id = l.id
          WHERE f.code = ${code.value}"""
      .query[(String, Option[String], LocalTime, LocalTime, String, String, String)]
      .option
      .transact(xa)
      .map(_.map((c, a, sd, sa, o, d, icao) => toFlight(c, a, sd, sa, o, d, icao)))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Flight]] =
    sql"""SELECT f.code, f.alias, f.sched_departure, f.sched_arrival, ao.iata_code, ad.iata_code, l.icao_code
          FROM flights f
            JOIN airports ao ON f.origin_airport_id = ao.id
            JOIN airports ad ON f.destination_airport_id = ad.id
            JOIN airlines l ON f.airline_id = l.id
          ORDER BY f.code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, Option[String], LocalTime, LocalTime, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((c, a, sd, sa, o, d, icao) => toFlight(c, a, sd, sa, o, d, icao)))
      .orDie

  override def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Flight]] =
    sql"""SELECT f.code, f.alias, f.sched_departure, f.sched_arrival, ao.iata_code, ad.iata_code, l.icao_code
          FROM flights f
            JOIN airports ao ON f.origin_airport_id = ao.id
            JOIN airports ad ON f.destination_airport_id = ad.id
            JOIN airlines l ON f.airline_id = l.id
          WHERE l.icao_code = ${icao.value}
          ORDER BY f.code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, Option[String], LocalTime, LocalTime, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((c, a, sd, sa, o, d, ic) => toFlight(c, a, sd, sa, o, d, ic)))
      .orDie

  override def findAirlineByCode(code: FlightCode): IO[DomainError, Option[Airline]] =
    sql"""SELECT l.icao_code, l.name, l.foundation_date
          FROM flights f JOIN airlines l ON f.airline_id = l.id
          WHERE f.code = ${code.value}"""
      .query[(String, String, LocalDate)]
      .option
      .transact(xa)
      .map(_.map((icao, name, foundationDate) => Airline(AirlineIcaoCode.unsafeMake(icao), name, foundationDate)))
      .orDie

  override def save(flight: Flight): IO[DomainError, Flight] =
    for {
      originId      <- resolveAirportId(flight.origin)
      destinationId <- resolveAirportId(flight.destination)
      airlineId     <- resolveAirlineId(flight.airlineIcao)
      result        <- sql"""
             INSERT INTO flights (code, alias, sched_departure, sched_arrival, origin_airport_id,
               destination_airport_id, airline_id)
             VALUES (${flight.code.value}, ${flight.alias}, ${flight.schedDeparture}, ${flight.schedArrival},
               $originId, $destinationId, $airlineId)
           """.update.run
                         .attemptSomeSqlState {
                           case sqlstate.class23.UNIQUE_VIOLATION => DomainError.FlightAlreadyExists(flight.code.value)
                         }
                         .transact(xa)
                         .orDie
                         .flatMap {
                           case Left(error) => ZIO.fail(error)
                           case Right(_)    => ZIO.succeed(flight)
                         }
    } yield result

  override def update(flight: Flight): IO[DomainError, Flight] =
    for {
      originId      <- resolveAirportId(flight.origin)
      destinationId <- resolveAirportId(flight.destination)
      airlineId     <- resolveAirlineId(flight.airlineIcao)
      result        <- sql"""
             UPDATE flights SET alias = ${flight.alias}, sched_departure = ${flight.schedDeparture},
               sched_arrival = ${flight.schedArrival}, origin_airport_id = $originId,
               destination_airport_id = $destinationId, airline_id = $airlineId
             WHERE code = ${flight.code.value}
           """.update.run
                         .transact(xa)
                         .orDie
                         .flatMap {
                           case 0L => ZIO.fail(DomainError.FlightNotFound(flight.code.value))
                           case _  => ZIO.succeed(flight)
                         }
    } yield result

  override def delete(code: FlightCode): IO[DomainError, Unit] =
    sql"DELETE FROM flights WHERE code = ${code.value}"
      .update.run
      .transact(xa)
      .orDie
      .flatMap {
        case 0 => ZIO.fail(DomainError.FlightNotFound(code.value))
        case _ => ZIO.unit
      }
}

object DoobieFlightRepository {
  val layer: URLayer[Transactor[Task], FlightRepository] =
    ZLayer.fromFunction(new DoobieFlightRepository(_))
}
