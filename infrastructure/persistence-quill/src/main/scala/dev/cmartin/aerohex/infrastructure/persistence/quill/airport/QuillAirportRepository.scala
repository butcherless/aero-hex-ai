package dev.cmartin.aerohex.infrastructure.persistence.quill.airport

import dev.cmartin.aerohex.infrastructure.persistence.quill.country.QuillCountryIdResolver
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.airport.{Airport, IataCode}
import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.airport.AirportRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, URLayer, ZIO, ZLayer}

import javax.sql.DataSource

final class QuillAirportRepository(dataSource: DataSource) extends AirportRepository with QuillCountryIdResolver {

  private case class AirportRow(
      id: Long,
      iataCode: String,
      icaoCode: String,
      name: String,
      city: String,
      countryId: Long
  )

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAirport(a: AirportRow): Airport =
    Airport(IataCode.unsafeMake(a.iataCode), IcaoCode.unsafeMake(a.icaoCode), a.name, a.city)

  override def findByIata(iata: IataCode): IO[DomainError, Option[Airport]] =
    ctx
      .run(quote {
        querySchema[AirportRow]("airports").filter(_.iataCode == lift(iata.value))
      })
      .map(_.headOption.map(toAirport))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        querySchema[AirportRow]("airports")
          .sortBy(_.iataCode)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toAirport))
      .orDie
  }

  override def searchByName(query: String): IO[DomainError, List[Airport]] = {
    val pattern = "%" + query + "%"
    ctx
      .run(quote {
        querySchema[AirportRow]("airports")
          .filter(r => infix"${r.name} ILIKE ${lift(pattern)}".as[Boolean])
          .sortBy(_.name)
      })
      .map(_.map(toAirport))
      .orDie
  }

  override def findByCountry(code: CountryCode, pagination: Pagination): IO[DomainError, List[Airport]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          a <- querySchema[AirportRow]("airports")
          c <- querySchema[CountryRef]("countries").join(c => c.id == a.countryId)
          if c.code == lift(code.value)
        } yield a)
          .sortBy(_.iataCode)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toAirport))
      .orDie
  }

  override def save(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport] =
    resolveCountryId(countryCode).flatMap { countryId =>
      QuillSqlState.refineUniqueViolation(
        ctx
          .run(quote {
            querySchema[AirportRow]("airports").insert(
              _.iataCode  -> lift(airport.iataCode.value),
              _.icaoCode  -> lift(airport.icaoCode.value),
              _.name      -> lift(airport.name),
              _.city      -> lift(airport.city),
              _.countryId -> lift(countryId)
            )
          })
          .as(airport)
      )(DomainError.AirportAlreadyExists(airport.iataCode.value))
    }

  override def update(airport: Airport, countryCode: CountryCode): IO[DomainError, Airport] =
    resolveCountryId(countryCode).flatMap { countryId =>
      ctx
        .run(quote {
          querySchema[AirportRow]("airports")
            .filter(_.iataCode == lift(airport.iataCode.value))
            .update(
              _.icaoCode  -> lift(airport.icaoCode.value),
              _.name      -> lift(airport.name),
              _.city      -> lift(airport.city),
              _.countryId -> lift(countryId)
            )
        })
        .orDie
        .flatMap {
          case 0L => ZIO.fail(DomainError.AirportNotFound(airport.iataCode.value))
          case _  => ZIO.succeed(airport)
        }
    }

  override def delete(iata: IataCode): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[AirportRow]("airports").filter(_.iataCode == lift(iata.value)).delete
      })
      .orDie
      .flatMap {
        case 0L => ZIO.fail(DomainError.AirportNotFound(iata.value))
        case _  => ZIO.unit
      }
}

object QuillAirportRepository {
  val layer: URLayer[DataSource, AirportRepository] =
    ZLayer.fromFunction(new QuillAirportRepository(_))
}
