package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.{Airport, CountryCode, IataCode}
import dev.cmartin.aerohex.domain.port.out.AirportRepository
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, URLayer, ZIO, ZLayer}

import java.sql.SQLException
import javax.sql.DataSource

final class QuillAirportRepository(dataSource: DataSource) extends AirportRepository {

  private val uniqueViolationSqlState = "23505"

  private case class AirportRow(
      id: Long,
      iataCode: String,
      icaoCode: String,
      name: String,
      city: String,
      countryId: Long
  )
  private case class CountryRef(id: Long, code: String)

  private val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAirport(row: (AirportRow, CountryRef)): Airport =
    val (a, c) = row
    Airport(IataCode(a.iataCode), a.icaoCode, a.name, a.city, CountryCode(c.code))

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

  override def findByIata(iata: IataCode): IO[DomainError, Option[Airport]] =
    ctx
      .run(quote {
        for {
          a <- querySchema[AirportRow]("airports").filter(_.iataCode == lift(iata.value))
          c <- querySchema[CountryRef]("countries").join(c => c.id == a.countryId)
        } yield (a, c)
      })
      .map(_.headOption.map(toAirport))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Airport]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          a <- querySchema[AirportRow]("airports")
          c <- querySchema[CountryRef]("countries").join(c => c.id == a.countryId)
        } yield (a, c))
          .sortBy(_._1.iataCode)
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
        (for {
          a <- querySchema[AirportRow]("airports")
                 .filter(r => infix"${r.name} ILIKE ${lift(pattern)}".as[Boolean])
          c <- querySchema[CountryRef]("countries").join(c => c.id == a.countryId)
        } yield (a, c))
          .sortBy(_._1.name)
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
        } yield (a, c))
          .sortBy(_._1.iataCode)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map(toAirport))
      .orDie
  }

  override def save(airport: Airport): IO[DomainError, Airport] =
    resolveCountryId(airport.countryCode).flatMap { countryId =>
      ctx
        .run(quote {
          querySchema[AirportRow]("airports").insert(
            _.iataCode  -> lift(airport.iataCode.value),
            _.icaoCode  -> lift(airport.icaoCode),
            _.name      -> lift(airport.name),
            _.city      -> lift(airport.city),
            _.countryId -> lift(countryId)
          )
        })
        .as(airport)
        .refineOrDie {
          case e: SQLException if e.getSQLState == uniqueViolationSqlState =>
            DomainError.AirportAlreadyExists(airport.iataCode.value)
        }
    }

  override def update(airport: Airport): IO[DomainError, Airport] =
    resolveCountryId(airport.countryCode).flatMap { countryId =>
      ctx
        .run(quote {
          querySchema[AirportRow]("airports")
            .filter(_.iataCode == lift(airport.iataCode.value))
            .update(
              _.icaoCode  -> lift(airport.icaoCode),
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
      .unit
      .orDie
}

object QuillAirportRepository {
  val layer: URLayer[DataSource, AirportRepository] =
    ZLayer.fromFunction(new QuillAirportRepository(_))
}
