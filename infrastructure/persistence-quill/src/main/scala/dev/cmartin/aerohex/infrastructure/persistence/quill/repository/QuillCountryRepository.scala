package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, UIO, URLayer, ZIO, ZLayer}

import java.sql.SQLException
import javax.sql.DataSource

final class QuillCountryRepository(dataSource: DataSource) extends CountryRepository {

  private val uniqueViolationSqlState = "23505"

  private case class CountryRow(code: String, name: String)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toCountry(row: CountryRow): Country =
    Country(CountryCode(row.code), row.name)

  override def findByCode(code: CountryCode): IO[DomainError, Option[Country]] =
    ctx
      .run(quote {
        querySchema[CountryRow]("countries").filter(_.code == lift(code.value))
      })
      .map(_.headOption.map(toCountry))
      .orDie

  override def findAll(pagination: Pagination): UIO[List[Country]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        querySchema[CountryRow]("countries")
          .sortBy(_.code)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toCountry))
      .orDie
  }

  override def searchByName(query: String): UIO[List[Country]] = {
    val pattern = "%" + query + "%"
    ctx
      .run(quote {
        querySchema[CountryRow]("countries")
          .filter(r => infix"${r.name} ILIKE ${lift(pattern)}".as[Boolean])
          .sortBy(_.name)
      })
      .map(_.map(toCountry))
      .orDie
  }

  override def save(country: Country): IO[DomainError, Country] = {
    val code = country.code.value
    val name = country.name
    ctx
      .run(quote {
        querySchema[CountryRow]("countries").insert(_.code -> lift(code), _.name -> lift(name))
      })
      .as(country)
      .refineOrDie {
        case e: SQLException if e.getSQLState == uniqueViolationSqlState =>
          DomainError.CountryAlreadyExists(code)
      }
  }

  override def update(country: Country): IO[DomainError, Country] =
    ctx
      .run(quote {
        querySchema[CountryRow]("countries")
          .filter(_.code == lift(country.code.value))
          .update(_.name -> lift(country.name))
      })
      .orDie
      .flatMap:
        case 0L => ZIO.fail(DomainError.CountryNotFound(country.code.value))
        case _  => ZIO.succeed(country)

  override def delete(code: CountryCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[CountryRow]("countries").filter(_.code == lift(code.value)).delete
      })
      .orDie
      .flatMap:
        case 0L => ZIO.fail(DomainError.CountryNotFound(code.value))
        case _  => ZIO.unit
}

object QuillCountryRepository {
  val layer: URLayer[DataSource, CountryRepository] =
    ZLayer.fromFunction(new QuillCountryRepository(_))
}
