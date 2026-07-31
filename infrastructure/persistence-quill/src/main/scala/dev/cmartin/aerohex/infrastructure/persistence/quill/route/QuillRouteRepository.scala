package dev.cmartin.aerohex.infrastructure.persistence.quill.route

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.route.{Route, RouteRepository}
import dev.cmartin.aerohex.infrastructure.persistence.quill.airport.QuillAirportIdResolver
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillSqlState
import dev.cmartin.aerohex.shared.Pagination
import io.getquill.*
import io.getquill.jdbczio.Quill
import javax.sql.DataSource
import zio.{IO, URLayer, ZLayer}

final class QuillRouteRepository(dataSource: DataSource) extends RouteRepository with QuillAirportIdResolver {

  private case class RouteRow(
      originAirportId: Long,
      destinationAirportId: Long,
      distanceKm: Int
  )

  protected val ctx = new Quill.Postgres(SnakeCase, dataSource)

  import ctx.*

  private def toRoute(row: RouteRow, originIata: String, destinationIata: String): Route =
    Route(IataCode.unsafeMake(originIata), IataCode.unsafeMake(destinationIata), row.distanceKm)

  override def findBySegment(origin: IataCode, destination: IataCode): IO[DomainError, Option[Route]] =
    ctx
      .run(quote {
        for {
          r  <- querySchema[RouteRow]("routes")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == r.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == r.destinationAirportId)
          if ao.iataCode == lift(origin.value) && ad.iataCode == lift(destination.value)
        } yield (r, ao.iataCode, ad.iataCode)
      })
      .map(_.headOption.map { case (r, oIata, dIata) => toRoute(r, oIata, dIata) })
      .orDie

  override def findAll(pagination: Pagination): IO[DomainError, List[Route]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          r  <- querySchema[RouteRow]("routes")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == r.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == r.destinationAirportId)
        } yield (r, ao.iataCode, ad.iataCode))
          .sortBy(_._2)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (r, oIata, dIata) => toRoute(r, oIata, dIata) })
      .orDie
  }

  override def findAllUnbounded: IO[DomainError, List[Route]] =
    ctx
      .run(quote {
        (for {
          r  <- querySchema[RouteRow]("routes")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == r.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == r.destinationAirportId)
        } yield (r, ao.iataCode, ad.iataCode))
          .sortBy(_._2)
      })
      .map(_.map { case (r, oIata, dIata) => toRoute(r, oIata, dIata) })
      .orDie

  override def findByOrigin(origin: IataCode, pagination: Pagination): IO[DomainError, List[Route]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          r  <- querySchema[RouteRow]("routes")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == r.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == r.destinationAirportId)
          if ao.iataCode == lift(origin.value)
        } yield (r, ao.iataCode, ad.iataCode))
          .sortBy(_._3)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (r, oIata, dIata) => toRoute(r, oIata, dIata) })
      .orDie
  }

  override def findByDestination(destination: IataCode, pagination: Pagination): IO[DomainError, List[Route]] = {
    val offset = pagination.offset
    val limit  = pagination.pageSize
    ctx
      .run(quote {
        (for {
          r  <- querySchema[RouteRow]("routes")
          ao <- querySchema[AirportRef]("airports").join(a => a.id == r.originAirportId)
          ad <- querySchema[AirportRef]("airports").join(a => a.id == r.destinationAirportId)
          if ad.iataCode == lift(destination.value)
        } yield (r, ao.iataCode, ad.iataCode))
          .sortBy(_._2)
          .drop(lift(offset))
          .take(lift(limit))
      })
      .map(_.map { case (r, oIata, dIata) => toRoute(r, oIata, dIata) })
      .orDie
  }

  override def save(route: Route): IO[DomainError, Route] =
    for {
      originId      <- resolveAirportId(route.origin)
      destinationId <- resolveAirportId(route.destination)
      result        <- QuillSqlState.refineUniqueViolation(
                         ctx
                           .run(quote {
                             querySchema[RouteRow]("routes").insert(
                               _.originAirportId      -> lift(originId),
                               _.destinationAirportId -> lift(destinationId),
                               _.distanceKm           -> lift(route.distanceKm)
                             )
                           })
                           .as(route)
                       )(DomainError.RouteAlreadyExists(route.origin.value, route.destination.value))
    } yield result

  override def update(route: Route): IO[DomainError, Route] =
    for {
      originId      <- resolveAirportId(route.origin)
      destinationId <- resolveAirportId(route.destination)
      result        <- QuillSqlState.refineZeroRows(
                         ctx.run(quote {
                           querySchema[RouteRow]("routes")
                             .filter(r =>
                               r.originAirportId == lift(originId) && r.destinationAirportId == lift(destinationId)
                             )
                             .update(_.distanceKm -> lift(route.distanceKm))
                         })
                       )(DomainError.RouteNotFound(route.origin.value, route.destination.value), route)
    } yield result

  override def delete(origin: IataCode, destination: IataCode): IO[DomainError, Unit] =
    for {
      originId      <- resolveAirportId(origin)
      destinationId <- resolveAirportId(destination)
      result        <- QuillSqlState.refineZeroRows(
                         ctx.run(quote {
                           querySchema[RouteRow]("routes")
                             .filter(r =>
                               r.originAirportId == lift(originId) && r.destinationAirportId == lift(destinationId)
                             )
                             .delete
                         })
                       )(DomainError.RouteNotFound(origin.value, destination.value), ())
    } yield result
}

object QuillRouteRepository {
  val layer: URLayer[DataSource, RouteRepository] =
    ZLayer.fromFunction(new QuillRouteRepository(_))
}
