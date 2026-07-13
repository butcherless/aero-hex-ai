package dev.cmartin.aerohex.infrastructure.persistence.quill.airport

import dev.cmartin.aerohex.domain.airport.IataCode
import dev.cmartin.aerohex.domain.error.DomainError
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, ZIO}

// Shared by every Quill repository whose table has an airport FK (origin/destination) — resolves
// the natural IataCode to its surrogate id, or fails with AirportNotFound. Mirrors
// QuillAirlineIdResolver's shape exactly.
private[quill] trait QuillAirportIdResolver:
  protected val ctx: Quill.Postgres[SnakeCase]
  import ctx.*

  protected case class AirportRef(id: Long, iataCode: String)

  protected def resolveAirportId(iata: IataCode): IO[DomainError, Long] =
    ctx
      .run(quote {
        querySchema[AirportRef]("airports").filter(_.iataCode == lift(iata.value)).map(_.id)
      })
      .orDie
      .flatMap {
        case id :: _ => ZIO.succeed(id)
        case Nil     => ZIO.fail(DomainError.AirportNotFound(iata.value))
      }
