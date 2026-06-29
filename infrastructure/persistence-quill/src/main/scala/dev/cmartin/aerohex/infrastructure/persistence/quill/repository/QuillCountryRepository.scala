package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Country, CountryCode}
import dev.cmartin.aerohex.domain.port.out.CountryRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, URLayer, ZLayer}

import javax.sql.DataSource

final class QuillCountryRepository(dataSource: DataSource) extends CountryRepository {

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
      .mapError(e => DomainError.DatabaseError(e.getMessage))

  override def findAll(pagination: Pagination): IO[DomainError, List[Country]] = {
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
      .mapError(e => DomainError.DatabaseError(e.getMessage))
  }

  override def searchByName(query: String): IO[DomainError, List[Country]] = {
    val pattern = "%" + query + "%"
    ctx
      .run(quote {
        querySchema[CountryRow]("countries")
          .filter(r => infix"${r.name} ILIKE ${lift(pattern)}".as[Boolean])
          .sortBy(_.name)
      })
      .map(_.map(toCountry))
      .mapError(e => DomainError.DatabaseError(e.getMessage))
  }

  override def save(country: Country): IO[DomainError, Country] = {
    val code = country.code.value
    val name = country.name
    ctx
      .run(quote {
        infix"""INSERT INTO countries (code, name) VALUES (${lift(code)}, ${lift(name)})
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name""".as[Action[CountryRow]]
      })
      .as(country)
      .mapError(e => DomainError.DatabaseError(e.getMessage))
  }

  override def delete(code: CountryCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[CountryRow]("countries").filter(_.code == lift(code.value)).delete
      })
      .unit
      .mapError(e => DomainError.DatabaseError(e.getMessage))
}

object QuillCountryRepository {
  val layer: URLayer[DataSource, CountryRepository] =
    ZLayer.fromFunction(new QuillCountryRepository(_))
}
