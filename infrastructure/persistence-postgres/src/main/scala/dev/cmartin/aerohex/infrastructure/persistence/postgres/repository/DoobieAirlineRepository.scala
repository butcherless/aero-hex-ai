package dev.cmartin.aerohex.infrastructure.persistence.postgres.repository

import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, CountryCode, IcaoCode}
import dev.cmartin.aerohex.domain.port.out.AirlineRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, ZIO, ZLayer, URLayer}
import zio.interop.catz.*

import java.time.LocalDate

final class DoobieAirlineRepository(xa: Transactor[Task]) extends AirlineRepository {

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

  override def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]] =
    sql"SELECT icao_code, name, foundation_date FROM airlines WHERE icao_code = ${icao.value}"
      .query[(String, String, LocalDate)]
      .option
      .transact(xa)
      .map(_.map((i, n, fd) => Airline(IcaoCode(i), n, fd)))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airline]] =
    sql"""SELECT icao_code, name, foundation_date FROM airlines
          ORDER BY icao_code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, LocalDate)]
      .to[List]
      .transact(xa)
      .map(_.map((i, n, fd) => Airline(IcaoCode(i), n, fd)))
      .orDie

  override def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      sql"""
        INSERT INTO airlines (icao_code, name, foundation_date, country_id)
        VALUES (${airline.icao.value}, ${airline.name}, ${airline.foundationDate}, $countryId)
        ON CONFLICT (icao_code) DO UPDATE
          SET name = EXCLUDED.name, foundation_date = EXCLUDED.foundation_date
      """.update.run
        .transact(xa)
        .as(airline)
        .orDie
    }

  override def delete(icao: IcaoCode): IO[DomainError, Unit] =
    sql"DELETE FROM airlines WHERE icao_code = ${icao.value}"
      .update.run
      .transact(xa)
      .unit
      .orDie
}

object DoobieAirlineRepository {
  val layer: URLayer[Transactor[Task], AirlineRepository] =
    ZLayer.fromFunction(new DoobieAirlineRepository(_))
}
