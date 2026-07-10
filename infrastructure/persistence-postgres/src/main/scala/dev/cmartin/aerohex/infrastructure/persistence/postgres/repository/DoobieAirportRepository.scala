package dev.cmartin.aerohex.infrastructure.persistence.postgres.repository

import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode, IcaoCode}
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, URLayer, ZIO, ZLayer}
import zio.interop.catz.*

final class DoobieAirportRepository(xa: Transactor[Task]) extends AirportRepository {

  private def resolveCountryId(code: CountryCode): IO[DomainError, Long] =
    sql"SELECT id FROM countries WHERE code = ${code.value}"
      .query[Long]
      .option
      .transact(xa)
      .orDie
      .flatMap {
        case Some(id) => ZIO.succeed(id)
        case None     => ZIO.fail(DomainError.CountryNotFound(code.value))
      }

  override def findByIata(iata: IataCode): IO[DomainError, Option[Airport]] =
    sql"SELECT iata_code, icao_code, name, city FROM airports WHERE iata_code = ${iata.value}"
      .query[(String, String, String, String)]
      .option
      .transact(xa)
      .map(_.map((i, icao, n, city) => Airport(IataCode(i), IcaoCode(icao), n, city)))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] =
    sql"""SELECT iata_code, icao_code, name, city FROM airports
          ORDER BY iata_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, city) => Airport(IataCode(i), IcaoCode(icao), n, city)))
      .orDie

  override def searchByName(query: String): IO[DomainError, List[Airport]] = {
    val pattern = s"%$query%"
    sql"""SELECT iata_code, icao_code, name, city FROM airports
          WHERE name ILIKE $pattern ORDER BY name"""
      .query[(String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, city) => Airport(IataCode(i), IcaoCode(icao), n, city)))
      .orDie
  }

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] =
    sql"""SELECT a.iata_code, a.icao_code, a.name, a.city
          FROM airports a JOIN countries c ON a.country_id = c.id
          WHERE c.code = ${code.value} ORDER BY a.iata_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((i, icao, n, city) => Airport(IataCode(i), IcaoCode(icao), n, city)))
      .orDie

  override def save(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport] =
    resolveCountryId(countryCode).flatMap { countryId =>
      sql"""
        INSERT INTO airports (iata_code, icao_code, name, city, country_id)
        VALUES (${airport.iataCode.value}, ${airport.icaoCode.value}, ${airport.name}, ${airport.city}, $countryId)
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.UNIQUE_VIOLATION      => DomainError.AirportAlreadyExists(airport.iataCode.value)
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(countryCode.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(_)    => ZIO.succeed(airport)
        }
    }

  override def update(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport] =
    resolveCountryId(countryCode).flatMap { countryId =>
      sql"""
        UPDATE airports SET icao_code = ${airport.icaoCode.value}, name = ${airport.name}, city = ${airport.city},
          country_id = $countryId
        WHERE iata_code = ${airport.iataCode.value}
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(countryCode.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(0L)   => ZIO.fail(DomainError.AirportNotFound(airport.iataCode.value))
          case Right(_)    => ZIO.succeed(airport)
        }
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
