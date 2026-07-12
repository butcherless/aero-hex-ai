package dev.cmartin.aerohex.infrastructure.persistence.quill.country

import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.country.{Country, CountryCode}
import dev.cmartin.aerohex.domain.country.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, UIO, URLayer, ZIO, ZLayer}

import javax.sql.DataSource

final class QuillCountryRepository(dataSource: DataSource) extends CountryRepository {

  private case class CountryRow(id: Long, code: String, name: String)
  private case class CountryCodeRow(code: String)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toCountry(row: CountryRow): Country =
    Country(CountryCode.unsafeMake(row.code), row.name)

  override def validateCode(code: CountryCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[CountryCodeRow]("country_codes").filter(_.code == lift(code.value))
      })
      .orDie
      .flatMap {
        case Nil => ZIO.fail(DomainError.InvalidCountryCode(code.value))
        case _   => ZIO.unit
      }

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
    QuillSqlState.refineUniqueViolation(
      ctx
        .run(quote {
          querySchema[CountryRow]("countries").insert(_.code -> lift(code), _.name -> lift(name))
        })
        .as(country)
    )(DomainError.CountryAlreadyExists(code))
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
