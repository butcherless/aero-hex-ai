package dev.cmartin.aerohex.infrastructure.persistence.quill.flight

import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{Flight, FlightCode, FlightRepository}
import dev.cmartin.aerohex.infrastructure.persistence.quill.airline.QuillAirlineIdResolver
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportIdResolver
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import java.time.{LocalDate, LocalTime}
import javax.sql.DataSource
import zio.{IO, URLayer, ZLayer}

final class QuillFlightRepository(dataSource: DataSource) extends FlightRepository
    with QuillAirportIdResolver with QuillAirlineIdResolver {

  private case class FlightRow(
      id: Long,
      code: String,
      alias: Option[String],
      schedDeparture: LocalTime,
      schedArrival: LocalTime,
      originAirportId: Long,
      destinationAirportId: Long,
      airlineId: Long
  )

  // Full airline row, not QuillAirlineIdResolver's AirlineRef (id/icaoCode only) —
  // findAirlineByCode needs to materialize a complete Airline, including name/foundationDate.
  private case class AirlineRow(id: Long, icaoCode: String, name: String, foundationDate: LocalDate)

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toFlight(row: FlightRow, originIata: String, destinationIata: String, airlineIcao: String): Flight =
    Flight(
      FlightCode.unsafeMake(row.code),
      row.alias,
      row.schedDeparture,
      row.schedArrival,
      IataCode.unsafeMake(originIata),
      IataCode.unsafeMake(destinationIata),
      AirlineIcaoCode.unsafeMake(airlineIcao)
    )

  override def findByCode(code: FlightCode): IO[DomainError, Option[Flight]] =
    ctx
      .run(quote {
        for {
          f  <- querySchema[FlightRow]("flights").filter(_.code == lift(code.value))
          ao <- querySchema[AirportRef]("airports").join(a => a.id == f.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == f.destinationAirportId)
          l  <- querySchema[AirlineRef]("airlines").join(l => l.id == f.airlineId)
        } yield (f, ao.iataCode, ad.iataCode, l.icaoCode)
      })
      .map(_.headOption.map { case (f, oIata, dIata, icao) => toFlight(f, oIata, dIata, icao) })
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Flight]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          f  <- querySchema[FlightRow]("flights")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == f.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == f.destinationAirportId)
          l  <- querySchema[AirlineRef]("airlines").join(l => l.id == f.airlineId)
        } yield (f, ao.iataCode, ad.iataCode, l.icaoCode))
          .sortBy(_._1.code)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (f, oIata, dIata, icao) => toFlight(f, oIata, dIata, icao) })
      .orDie
  }

  override def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Flight]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          f  <- querySchema[FlightRow]("flights")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == f.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == f.destinationAirportId)
          l  <- querySchema[AirlineRef]("airlines").join(l => l.id == f.airlineId)
          if l.icaoCode == lift(icao.value)
        } yield (f, ao.iataCode, ad.iataCode, l.icaoCode))
          .sortBy(_._1.code)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (f, oIata, dIata, aIcao) => toFlight(f, oIata, dIata, aIcao) })
      .orDie
  }

  override def findAirlineByCode(code: FlightCode): IO[DomainError, Option[Airline]] =
    ctx
      .run(quote {
        for {
          f <- querySchema[FlightRow]("flights").filter(_.code == lift(code.value))
          l <- querySchema[AirlineRow]("airlines").join(l => l.id == f.airlineId)
        } yield l
      })
      .map(_.headOption.map(l => Airline(AirlineIcaoCode.unsafeMake(l.icaoCode), l.name, l.foundationDate)))
      .orDie

  override def save(flight: Flight): IO[DomainError, Flight] =
    for {
      originId      <- resolveAirportId(flight.origin)
      destinationId <- resolveAirportId(flight.destination)
      airlineId     <- resolveAirlineId(flight.airlineIcao)
      result        <- QuillSqlState.refineUniqueViolation(
                         ctx
                           .run(quote {
                             querySchema[FlightRow]("flights").insert(
                               _.code                 -> lift(flight.code.value),
                               _.alias                -> lift(flight.alias),
                               _.schedDeparture       -> lift(flight.schedDeparture),
                               _.schedArrival         -> lift(flight.schedArrival),
                               _.originAirportId      -> lift(originId),
                               _.destinationAirportId -> lift(destinationId),
                               _.airlineId            -> lift(airlineId)
                             )
                           })
                           .as(flight)
                       )(DomainError.FlightAlreadyExists(flight.code.value))
    } yield result

  override def update(flight: Flight): IO[DomainError, Flight] =
    for {
      originId      <- resolveAirportId(flight.origin)
      destinationId <- resolveAirportId(flight.destination)
      airlineId     <- resolveAirlineId(flight.airlineIcao)
      result        <- QuillSqlState.refineZeroRows(
                         ctx.run(quote {
                           querySchema[FlightRow]("flights")
                             .filter(_.code == lift(flight.code.value))
                             .update(
                               _.alias                -> lift(flight.alias),
                               _.schedDeparture       -> lift(flight.schedDeparture),
                               _.schedArrival         -> lift(flight.schedArrival),
                               _.originAirportId      -> lift(originId),
                               _.destinationAirportId -> lift(destinationId),
                               _.airlineId            -> lift(airlineId)
                             )
                         })
                       )(DomainError.FlightNotFound(flight.code.value), flight)
    } yield result

  override def delete(code: FlightCode): IO[DomainError, Unit] =
    QuillSqlState.refineZeroRows(
      ctx.run(quote {
        querySchema[FlightRow]("flights").filter(_.code == lift(code.value)).delete
      })
    )(DomainError.FlightNotFound(code.value), ())
}

object QuillFlightRepository {
  val layer: URLayer[DataSource, FlightRepository] =
    ZLayer.fromFunction(new QuillFlightRepository(_))
}
