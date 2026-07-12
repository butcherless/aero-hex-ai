package dev.cmartin.aerohex.infrastructure.persistence.quill.aircraft

import dev.cmartin.aerohex.domain.aircraft.AircraftRepository
import dev.cmartin.aerohex.domain.aircraft.{Aircraft, Registration}
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineIdResolver
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import javax.sql.DataSource
import zio.{IO, URLayer, ZIO, ZLayer}

final class QuillAircraftRepository(dataSource: DataSource) extends AircraftRepository with QuillAirlineIdResolver {

  private case class AircraftRow(
      id: Long,
      registration: String,
      typeCode: String,
      description: String,
      airlineId: Long
  )

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toAircraft(row: AircraftRow, airlineIcao: String): Aircraft =
    Aircraft(Registration.unsafeMake(row.registration), row.typeCode, row.description, IcaoCode.unsafeMake(airlineIcao))

  override def findByRegistration(registration: Registration): IO[DomainError, Option[Aircraft]] =
    ctx
      .run(quote {
        for {
          a <- querySchema[AircraftRow]("aircraft").filter(_.registration == lift(registration.value))
          l <- querySchema[AirlineRef]("airlines").join(l => l.id == a.airlineId)
        } yield (a, l.icaoCode)
      })
      .map(_.headOption.map { case (a, icao) => toAircraft(a, icao) })
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          a <- querySchema[AircraftRow]("aircraft")
          l <- querySchema[AirlineRef]("airlines").join(l => l.id == a.airlineId)
        } yield (a, l.icaoCode))
          .sortBy(_._1.registration)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (a, icao) => toAircraft(a, icao) })
      .orDie
  }

  override def save(aircraft: Aircraft): IO[DomainError, Aircraft] =
    resolveAirlineId(aircraft.airlineIcao).flatMap { airlineId =>
      QuillSqlState.refineUniqueViolation(
        ctx
          .run(quote {
            querySchema[AircraftRow]("aircraft").insert(
              _.registration -> lift(aircraft.registration.value),
              _.typeCode     -> lift(aircraft.typeCode),
              _.description  -> lift(aircraft.description),
              _.airlineId    -> lift(airlineId)
            )
          })
          .as(aircraft)
      )(DomainError.AircraftAlreadyExists(aircraft.registration.value))
    }

  override def update(aircraft: Aircraft): IO[DomainError, Aircraft] =
    resolveAirlineId(aircraft.airlineIcao).flatMap { airlineId =>
      ctx
        .run(quote {
          querySchema[AircraftRow]("aircraft")
            .filter(_.registration == lift(aircraft.registration.value))
            .update(
              _.typeCode    -> lift(aircraft.typeCode),
              _.description -> lift(aircraft.description),
              _.airlineId   -> lift(airlineId)
            )
        })
        .orDie
        .flatMap {
          case 0L => ZIO.fail(DomainError.AircraftNotFound(aircraft.registration.value))
          case _  => ZIO.succeed(aircraft)
        }
    }

  override def delete(registration: Registration): IO[DomainError, Unit] =
    ctx
      .run(quote {
        querySchema[AircraftRow]("aircraft").filter(_.registration == lift(registration.value)).delete
      })
      .orDie
      .flatMap {
        case 0L => ZIO.fail(DomainError.AircraftNotFound(registration.value))
        case _  => ZIO.unit
      }
}

object QuillAircraftRepository {
  val layer: URLayer[DataSource, AircraftRepository] =
    ZLayer.fromFunction(new QuillAircraftRepository(_))
}
