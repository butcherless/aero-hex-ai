package dev.cmartin.aerohex.infrastructure.persistence.postgres.repository

import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode}
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, URLayer, ZIO, ZLayer}
import zio.interop.catz.*

final class DoobieAirportRepository(xa: Transactor[Task]) extends AirportRepository {

  override def findByIata(iata: IataCode): IO[DomainError, Option[Airport]] =
    sql"SELECT iata_code, icao_code, name, city, country_code FROM airports WHERE iata_code = ${iata.value}"
      .query[(String, String, String, String, String)]
      .option
      .transact(xa)
      .map(_.map((i, icao, n, c, cc) => Airport(IataCode(i), icao, n, c, CountryCode(cc))))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] =
    sql"SELECT iata_code, icao_code, name, city, country_code FROM airports ORDER BY iata_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"
      .query[(String, String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, c, cc) => Airport(IataCode(i), icao, n, c, CountryCode(cc))))
      .orDie

  override def searchByName(query: String): IO[DomainError, List[Airport]] = {
    val pattern = s"%$query%"
    sql"SELECT iata_code, icao_code, name, city, country_code FROM airports WHERE name ILIKE $pattern ORDER BY name"
      .query[(String, String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, c, cc) => Airport(IataCode(i), icao, n, c, CountryCode(cc))))
      .orDie
  }

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] =
    sql"""SELECT iata_code, icao_code, name, city, country_code FROM airports
          WHERE country_code = ${code.value} ORDER BY iata_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, c, cc) => Airport(IataCode(i), icao, n, c, CountryCode(cc))))
      .orDie

  override def save(airport: Airport): IO[DomainError, Airport] =
    sql"""
      INSERT INTO airports (iata_code, icao_code, name, city, country_code)
      VALUES (${airport.iataCode.value}, ${airport.icaoCode}, ${airport.name}, ${airport.city}, ${airport.countryCode.value})
    """.update.run
      .attemptSomeSqlState {
        case sqlstate.class23.UNIQUE_VIOLATION      => DomainError.AirportAlreadyExists(airport.iataCode.value)
        case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(airport.countryCode.value)
      }
      .transact(xa)
      .orDie
      .flatMap {
        case Left(error) => ZIO.fail(error)
        case Right(_)    => ZIO.succeed(airport)
      }

  override def delete(iata: IataCode): IO[DomainError, Unit] =
    sql"DELETE FROM airports WHERE iata_code = ${iata.value}"
      .update.run
      .transact(xa)
      .unit
      .orDie
}

object DoobieAirportRepository {
  val layer: URLayer[Transactor[Task], AirportRepository] =
    ZLayer.fromFunction(new DoobieAirportRepository(_))
}
