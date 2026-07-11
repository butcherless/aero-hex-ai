package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.model.IcaoCode
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, ZIO}

// Shared by every Quill repository whose table has an airline_id FK — resolves the
// natural IcaoCode to its surrogate id, or fails with AirlineNotFound.
private[repository] trait QuillAirlineIdResolver:
  protected val ctx: Quill.Postgres[SnakeCase]
  import ctx.*

  protected case class AirlineRef(id: Long, icaoCode: String)

  protected def resolveAirlineId(icao: IcaoCode): IO[DomainError, Long] =
    ctx
      .run(quote {
        querySchema[AirlineRef]("airlines").filter(_.icaoCode == lift(icao.value)).map(_.id)
      })
      .orDie
      .flatMap {
        case id :: _ => ZIO.succeed(id)
        case Nil     => ZIO.fail(DomainError.AirlineNotFound(icao.value))
      }
