package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airline, CountryCode, IcaoCode}
import dev.cmartin.aerohex.domain.port.out.AirlineRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, URLayer, ZIO, ZLayer}

import java.sql.SQLException
import java.time.LocalDate
import javax.sql.DataSource

final class QuillAirlineRepository(dataSource: DataSource) extends AirlineRepository {

  private val uniqueViolationSqlState = "23505"

  private case class AirlineRow(
      id: Long,
      icaoCode: String,
      name: String,
      foundationDate: LocalDate,
      countryId: Long
  )
  private case class CountryRef(id: Long, code: String)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAirline(a: AirlineRow): Airline =
    Airline(IcaoCode(a.icaoCode), a.name, a.foundationDate)

  private def resolveCountryId(code: CountryCode): IO[DomainError, Long] =
    ctx
      .run(quote {
        querySchema[CountryRef]("countries").filter(_.code == lift(code.value)).map(_.id)
      })
      .orDie
      .flatMap {
        case id :: _ => ZIO.succeed(id)
        case Nil     => ZIO.fail(DomainError.CountryNotFound(code.value))
      }

  override def findByIcao(icao: IcaoCode): IO[DomainError, Option[Airline]] =
    ctx
      .run(quote {
        querySchema[AirlineRow]("airlines").filter(_.icaoCode == lift(icao.value))
      })
      .map(_.headOption.map(toAirline))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airline]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        querySchema[AirlineRow]("airlines")
          .sortBy(_.icaoCode)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toAirline))
      .orDie
  }

  override def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      ctx
        .run(quote {
          querySchema[AirlineRow]("airlines").insert(
            _.icaoCode       -> lift(airline.icao.value),
            _.name           -> lift(airline.name),
            _.foundationDate -> lift(airline.foundationDate),
            _.countryId      -> lift(countryId)
          )
        })
        .as(airline)
        .refineOrDie {
          case e: SQLException if e.getSQLState == uniqueViolationSqlState =>
            DomainError.AirlineAlreadyExists(airline.icao.value)
        }
    }

  override def delete(icao: IcaoCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[AirlineRow]("airlines").filter(_.icaoCode == lift(icao.value)).delete
      })
      .unit
      .orDie
}

object QuillAirlineRepository {
  val layer: URLayer[DataSource, AirlineRepository] =
    ZLayer.fromFunction(new QuillAirlineRepository(_))
}
