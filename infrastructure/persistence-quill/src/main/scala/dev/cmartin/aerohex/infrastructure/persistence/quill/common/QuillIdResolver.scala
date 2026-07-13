package dev.cmartin.aerohex.infrastructure.persistence.quill.common

import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, UIO, ZIO}

// Shared by every Quill natural-key-to-surrogate-id resolver (Country/Airport/Airline) — turns
// the already-executed query result into a surrogate id, or fails with the caller-supplied
// DomainError when no row matches. Mirrors DoobieIdResolver's shape in persistence-postgres.
// The `ctx.run(quote { ... })` call itself must stay in each entity-specific resolver (not here):
// Quill's macro needs the quoted query fully known at its own call site to derive a Decoder —
// passing a Quoted[Query[Long]] through this trait's own method turns it into an undecodable
// "Dynamic Query" at compile time.
private[quill] trait QuillIdResolver:
  protected def resolveId(rows: UIO[List[Long]], notFound: => DomainError): IO[DomainError, Long] =
    rows.flatMap {
      case id :: _ => ZIO.succeed(id)
      case Nil     => ZIO.fail(notFound)
    }
