package dev.cmartin.aerohex.infrastructure.persistence.quill.airline

import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillIdResolver
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.IO

// Shared by every Quill repository whose table has an airline_id FK — resolves the
// natural AirlineIcaoCode to its surrogate id, or fails with AirlineNotFound.
private[quill] trait QuillAirlineIdResolver extends QuillIdResolver:
  protected val ctx: Quill.Postgres[SnakeCase]
  import ctx.*

  protected case class AirlineRef(id: Long, icaoCode: String)

  protected def resolveAirlineId(icao: AirlineIcaoCode): IO[DomainError, Long] =
    resolveId(
      ctx
        .run(quote {
          querySchema[AirlineRef]("airlines").filter(_.icaoCode == lift(icao.value)).map(_.id)
        })
        .orDie,
      DomainError.AirlineNotFound(icao.value)
    )
