package dev.cmartin.aerohex.infrastructure.persistence.quill.country

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, ZIO}

// Shared by every Quill repository whose table has a country_id FK — resolves the
// natural CountryCode to its surrogate id, or fails with CountryNotFound.
private[quill] trait QuillCountryIdResolver:
  protected val ctx: Quill.Postgres[SnakeCase]
  import ctx.*

  protected case class CountryRef(id: Long, code: String)

  protected def resolveCountryId(code: CountryCode): IO[DomainError, Long] =
    ctx
      .run(quote {
        querySchema[CountryRef]("countries").filter(_.code == lift(code.value)).map(_.id)
      })
      .orDie
      .flatMap {
        case id :: _ => ZIO.succeed(id)
        case Nil     => ZIO.fail(DomainError.CountryNotFound(code.value))
      }
