package dev.cmartin.aerohex.infrastructure.persistence.quill.airline

import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.{Airline, IcaoCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryIdResolver
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import java.time.LocalDate
import javax.sql.DataSource
import zio.{IO, URLayer, ZIO, ZLayer}

final class QuillAirlineRepository(dataSource: DataSource) extends AirlineRepository with QuillCountryIdResolver {

  private case class AirlineRow(
      id: Long,
      icaoCode: String,
      name: String,
      foundationDate: LocalDate,
      countryId: Long
  )

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAirline(a: AirlineRow): Airline =
    Airline(IcaoCode.unsafeMake(a.icaoCode), a.name, a.foundationDate)

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
      QuillSqlState.refineUniqueViolation(
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
      )(DomainError.AirlineAlreadyExists(airline.icao.value))
    }

  override def update(airline: Airline, countryCode: CountryCode): IO[DomainError, Airline] =
    resolveCountryId(countryCode).flatMap { countryId =>
      ctx
        .run(quote {
          querySchema[AirlineRow]("airlines")
            .filter(_.icaoCode == lift(airline.icao.value))
            .update(
              _.name           -> lift(airline.name),
              _.foundationDate -> lift(airline.foundationDate),
              _.countryId      -> lift(countryId)
            )
        })
        .orDie
        .flatMap {
          case 0L => ZIO.fail(DomainError.AirlineNotFound(airline.icao.value))
          case _  => ZIO.succeed(airline)
        }
    }

  override def delete(icao: IcaoCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[AirlineRow]("airlines").filter(_.icaoCode == lift(icao.value)).delete
      })
      .orDie
      .flatMap {
        case 0L => ZIO.fail(DomainError.AirlineNotFound(icao.value))
        case _  => ZIO.unit
      }
}

object QuillAirlineRepository {
  val layer: URLayer[DataSource, AirlineRepository] =
    ZLayer.fromFunction(new QuillAirlineRepository(_))
}
