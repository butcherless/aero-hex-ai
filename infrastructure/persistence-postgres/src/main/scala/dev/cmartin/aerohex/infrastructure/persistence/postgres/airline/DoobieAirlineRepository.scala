package dev.cmartin.aerohex.infrastructure.persistence.postgres.airline

import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.postgres.common.DoobieIdResolver
import dev.cmartin.aerohex.shared.Pagination
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.*
import zio.interop.catz.*
import zio.{IO, Task, URLayer, ZIO, ZLayer}

final class DoobieAirlineRepository(protected val xa: Transactor[Task]) extends AirlineRepository
    with DoobieIdResolver {

  private def resolveCountryId(code: CountryCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM countries WHERE code = ${code.value}".query[Long],
      DomainError.CountryNotFound(code.value)
    )

  override def findByIcao(icao: AirlineIcaoCode): IO[DomainError, Option[Airline]] =
    sql"SELECT icao_code, name, alias, callsign FROM airlines WHERE icao_code = ${icao.value}"
      .query[(String, String, Option[String], Option[String])]
      .option
      .transact(xa)
      .map(_.map((i, n, alias, callsign) => Airline(AirlineIcaoCode.unsafeMake(i), n, alias, callsign)))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airline]] =
    sql"""SELECT icao_code, name, alias, callsign FROM airlines
          ORDER BY icao_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, Option[String], Option[String])]
      .to[List]
      .transact(xa)
      .map(_.map((i, n, alias, callsign) => Airline(AirlineIcaoCode.unsafeMake(i), n, alias, callsign)))
      .orDie

  override def findAllUnbounded: IO[DomainError, List[Airline]] =
    sql"SELECT icao_code, name, alias, callsign FROM airlines ORDER BY icao_code"
      .query[(String, String, Option[String], Option[String])]
      .to[List]
      .transact(xa)
      .map(_.map((i, n, alias, callsign) => Airline(AirlineIcaoCode.unsafeMake(i), n, alias, callsign)))
      .orDie

  override def findAllUnboundedWithCountry: IO[DomainError, List[(Airline, CountryCode)]] =
    sql"""SELECT a.icao_code, a.name, a.alias, a.callsign, c.code
          FROM airlines a JOIN countries c ON a.country_id = c.id
          ORDER BY a.icao_code"""
      .query[(String, String, Option[String], Option[String], String)]
      .to[List]
      .transact(xa)
      .map(_.map { case (i, n, alias, callsign, countryCode) =>
        (Airline(AirlineIcaoCode.unsafeMake(i), n, alias, callsign), CountryCode.unsafeMake(countryCode))
      })
      .orDie

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]] =
    sql"""SELECT a.icao_code, a.name, a.alias, a.callsign
          FROM airlines a JOIN countries c ON a.country_id = c.id
          WHERE c.code = ${code.value} ORDER BY a.icao_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, Option[String], Option[String])]
      .to[List]
      .transact(xa)
      .map(_.map((i, n, alias, callsign) => Airline(AirlineIcaoCode.unsafeMake(i), n, alias, callsign)))
      .orDie

  override def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      sql"""
        INSERT INTO airlines (icao_code, name, alias, callsign, country_id)
        VALUES (${airline.icao.value}, ${airline.name}, ${airline.alias}, ${airline.callsign}, $countryId)
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.UNIQUE_VIOLATION      => DomainError.AirlineAlreadyExists(airline.icao.value)
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(countryCode.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(_)    => ZIO.succeed(airline)
        }
    }

  override def update(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      sql"""
        UPDATE airlines SET name = ${airline.name}, alias = ${airline.alias}, callsign = ${airline.callsign},
          country_id = $countryId
        WHERE icao_code = ${airline.icao.value}
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.CountryNotFound(countryCode.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(0L)   => ZIO.fail(DomainError.AirlineNotFound(airline.icao.value))
          case Right(_)    => ZIO.succeed(airline)
        }
    }

  override def delete(icao: AirlineIcaoCode): IO[DomainError, Unit] =
    sql"DELETE FROM airlines WHERE icao_code = ${icao.value}"
      .update.run
      .transact(xa)
      .orDie
      .flatMap {
        case 0 => ZIO.fail(DomainError.AirlineNotFound(icao.value))
        case _ => ZIO.unit
      }
}

object DoobieAirlineRepository {
  val layer: URLayer[Transactor[Task], AirlineRepository] =
    ZLayer.fromFunction(new DoobieAirlineRepository(_))
}
