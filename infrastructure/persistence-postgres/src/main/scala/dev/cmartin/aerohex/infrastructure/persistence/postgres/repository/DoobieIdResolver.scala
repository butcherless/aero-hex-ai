package dev.cmartin.aerohex.infrastructure.persistence.postgres.repository

import doobie.Query0
import doobie.Transactor
import doobie.implicits.*
import dev.cmartin.aerohex.domain.error.DomainError
import zio.{IO, Task, ZIO}
import zio.interop.catz.*

// Shared by every Doobie repository that resolves a natural key to a surrogate id
// (country_id, airport_id, airline_id FKs) — runs the given single-column query and
// fails with the caller-supplied DomainError when no row matches.
private[repository] trait DoobieIdResolver:
  protected def xa: Transactor[Task]

  protected def resolveId(query: Query0[Long], notFound: => DomainError): IO[DomainError, Long] =
    query.option.transact(xa).orDie.flatMap {
      case Some(id) => ZIO.succeed(id)
      case None     => ZIO.fail(notFound)
    }
