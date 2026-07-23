package dev.cmartin.aerohex.infrastructure.persistence.quill.airline

import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryIdResolver
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import javax.sql.DataSource
import zio.{IO, URLayer, ZLayer}

final class QuillAirlineRepository(dataSource: DataSource) extends AirlineRepository with QuillCountryIdResolver {

  private case class AirlineRow(
      id: Long,
      icaoCode: String,
      name: String,
      alias: Option[String],
      callsign: Option[String],
      countryId: Long
  )

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAirline(a: AirlineRow): Airline =
    Airline(AirlineIcaoCode.unsafeMake(a.icaoCode), a.name, a.alias, a.callsign)

  override def findByIcao(icao: AirlineIcaoCode): IO[DomainError, Option[Airline]] =
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

  override def findAllUnbounded: IO[DomainError, List[Airline]] =
    ctx
      .run(quote {
        querySchema[AirlineRow]("airlines").sortBy(_.icaoCode)
      })
      .map(_.map(toAirline))
      .orDie

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airline]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          a <- querySchema[AirlineRow]("airlines")
          c <- querySchema[CountryRef]("countries").join(c => c.id == a.countryId)
          if c.code == lift(code.value)
        } yield a)
          .sortBy(_.icaoCode)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toAirline))
      .orDie
  }

  override def save(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      QuillSqlState.refineUniqueViolation(
        ctx
          .run(quote {
            querySchema[AirlineRow]("airlines").insert(
              _.icaoCode  -> lift(airline.icao.value),
              _.name      -> lift(airline.name),
              _.alias     -> lift(airline.alias),
              _.callsign  -> lift(airline.callsign),
              _.countryId -> lift(countryId)
            )
          })
          .as(airline)
      )(DomainError.AirlineAlreadyExists(airline.icao.value))
    }

  override def update(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      QuillSqlState.refineZeroRows(
        ctx.run(quote {
          querySchema[AirlineRow]("airlines")
            .filter(_.icaoCode == lift(airline.icao.value))
            .update(
              _.name      -> lift(airline.name),
              _.alias     -> lift(airline.alias),
              _.callsign  -> lift(airline.callsign),
              _.countryId -> lift(countryId)
            )
        })
      )(DomainError.AirlineNotFound(airline.icao.value), airline)
    }

  override def delete(icao: AirlineIcaoCode): IO[DomainError, Unit] =
    QuillSqlState.refineZeroRows(
      ctx.run(quote {
        querySchema[AirlineRow]("airlines").filter(_.icaoCode == lift(icao.value)).delete
      })
    )(DomainError.AirlineNotFound(icao.value), ())
}

object QuillAirlineRepository {
  val layer: URLayer[DataSource, AirlineRepository] =
    ZLayer.fromFunction(new QuillAirlineRepository(_))
}
