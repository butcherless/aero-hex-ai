package dev.cmartin.aerohex.infrastructure.persistence.quill.country

import dev.cmartin.aerohex.domain.country.CountryCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.infrastructure.persistence.quill.common.QuillIdResolver
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.IO

// Shared by every Quill repository whose table has a country_id FK — resolves the
// natural CountryCode to its surrogate id, or fails with CountryNotFound.
private[quill] trait QuillCountryIdResolver extends QuillIdResolver:
  protected val ctx: Quill.Postgres[SnakeCase]
  import ctx.*

  protected case class CountryRef(id: Long, code: String)

  protected def resolveCountryId(code: CountryCode): IO[DomainError, Long] =
    resolveId(
      ctx
        .run(quote {
          querySchema[CountryRef]("countries").filter(_.code == lift(code.value)).map(_.id)
        })
        .orDie,
      DomainError.CountryNotFound(code.value)
    )
