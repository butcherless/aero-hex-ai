package dev.cmartin.aerohex.infrastructure.persistence.postgres.repository

import doobie.Transactor
import doobie.implicits.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, UIO, URLayer, ZIO, ZLayer}
import zio.interop.catz.*

final class DoobieCountryRepository(xa: Transactor[Task]) extends CountryRepository {

  override def validateCode(code: CountryCode): IO[DomainError, Unit] =
    sql"SELECT code FROM country_codes WHERE code = ${code.value}"
      .query[String]
      .option
      .transact(xa)
      .orDie
      .flatMap {
        case None    => ZIO.fail(DomainError.InvalidCountryCode(code.value))
        case Some(_) => ZIO.unit
      }

  override def findByCode(code: CountryCode): IO[DomainError, Option[Country]] =
    sql"SELECT code, name FROM countries WHERE code = ${code.value}"
      .query[(String, String)]
      .option
      .transact(xa)
      .map(_.map((c, n) => Country(CountryCode.unsafeMake(c), n)))
      .orDie

  override def findAll(pagination: Pagination): UIO[List[Country]] =
    sql"SELECT code, name FROM countries ORDER BY code LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"
      .query[(String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((c, n) => Country(CountryCode.unsafeMake(c), n)))
      .orDie

  override def searchByName(query: String): UIO[List[Country]] =
    sql"SELECT code, name FROM countries WHERE name ILIKE ${"%" + query + "%"} ORDER BY name"
      .query[(String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((c, n) => Country(CountryCode.unsafeMake(c), n)))
      .orDie

  override def save(country: Country): IO[DomainError, Country] =
    sql"""
      INSERT INTO countries (code, name)
      VALUES (${country.code.value}, ${country.name})
      ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
    """.update.run
      .transact(xa)
      .as(country)
      .orDie

  override def update(country: Country): IO[DomainError, Country] =
    sql"UPDATE countries SET name = ${country.name} WHERE code = ${country.code.value}"
      .update.run
      .transact(xa)
      .orDie
      .flatMap:
        case 0 => ZIO.fail(DomainError.CountryNotFound(country.code.value))
        case _ => ZIO.succeed(country)

  override def delete(code: CountryCode): IO[DomainError, Unit] =
    sql"DELETE FROM countries WHERE code = ${code.value}"
      .update.run
      .transact(xa)
      .orDie
      .flatMap:
        case 0 => ZIO.fail(DomainError.CountryNotFound(code.value))
        case _ => ZIO.unit
}

object DoobieCountryRepository {
  val layer: URLayer[Transactor[Task], CountryRepository] =
    ZLayer.fromFunction(new DoobieCountryRepository(_))
}
