package dev.cmartin.aerohex.infrastructure.persistence.postgres.aircraft

import dev.cmartin.aerohex.infrastructure.persistence.postgres.common.DoobieIdResolver
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.aircraft.{Aircraft, Registration}
import dev.cmartin.aerohex.domain.airline.IcaoCode
import dev.cmartin.aerohex.domain.aircraft.AircraftRepository
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, Task, URLayer, ZIO, ZLayer}
import zio.interop.catz.*

final class DoobieAircraftRepository(protected val xa: Transactor[Task]) extends AircraftRepository
    with DoobieIdResolver {

  private def resolveAirlineId(icao: IcaoCode): IO[DomainError, Long] =
    resolveId(
      sql"SELECT id FROM airlines WHERE icao_code = ${icao.value}".query[Long],
      DomainError.AirlineNotFound(icao.value)
    )

  override def findByRegistration(registration: Registration): IO[DomainError, Option[Aircraft]] =
    sql"""SELECT a.registration, a.type_code, a.description, l.icao_code
          FROM aircraft a JOIN airlines l ON a.airline_id = l.id
          WHERE a.registration = ${registration.value}"""
      .query[(String, String, String, String)]
      .option
      .transact(xa)
      .map(_.map((reg, tc, desc, icao) => Aircraft(Registration.unsafeMake(reg), tc, desc, IcaoCode.unsafeMake(icao))))
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Aircraft]] =
    sql"""SELECT a.registration, a.type_code, a.description, l.icao_code
          FROM aircraft a JOIN airlines l ON a.airline_id = l.id
          ORDER BY a.registration LIMIT ${pagination.pageSize} OFFSET ${pagination.offset}"""
      .query[(String, String, String, String)]
      .to[List]
      .transact(xa)
      .map(_.map((reg, tc, desc, icao) => Aircraft(Registration.unsafeMake(reg), tc, desc, IcaoCode.unsafeMake(icao))))
      .orDie

  override def save(aircraft: Aircraft): IO[DomainError, Aircraft] =
    resolveAirlineId(aircraft.airlineIcao).flatMap { airlineId =>
      sql"""
        INSERT INTO aircraft (registration, type_code, description, airline_id)
        VALUES (${aircraft.registration.value}, ${aircraft.typeCode}, ${aircraft.description}, $airlineId)
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.UNIQUE_VIOLATION      => DomainError.AircraftAlreadyExists(aircraft.registration.value)
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.AirlineNotFound(aircraft.airlineIcao.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(_)    => ZIO.succeed(aircraft)
        }
    }

  override def update(aircraft: Aircraft): IO[DomainError, Aircraft] =
    resolveAirlineId(aircraft.airlineIcao).flatMap { airlineId =>
      sql"""
        UPDATE aircraft SET type_code = ${aircraft.typeCode}, description = ${aircraft.description},
          airline_id = $airlineId
        WHERE registration = ${aircraft.registration.value}
      """.update.run
        .attemptSomeSqlState {
          case sqlstate.class23.FOREIGN_KEY_VIOLATION => DomainError.AirlineNotFound(aircraft.airlineIcao.value)
        }
        .transact(xa)
        .orDie
        .flatMap {
          case Left(error) => ZIO.fail(error)
          case Right(0L)   => ZIO.fail(DomainError.AircraftNotFound(aircraft.registration.value))
          case Right(_)    => ZIO.succeed(aircraft)
        }
    }

  override def delete(registration: Registration): IO[DomainError, Unit] =
    sql"DELETE FROM aircraft WHERE registration = ${registration.value}"
      .update.run
      .transact(xa)
      .orDie
      .flatMap {
        case 0 => ZIO.fail(DomainError.AircraftNotFound(registration.value))
        case _ => ZIO.unit
      }
}

object DoobieAircraftRepository {
  val layer: URLayer[Transactor[Task], AircraftRepository] =
    ZLayer.fromFunction(new DoobieAircraftRepository(_))
}
