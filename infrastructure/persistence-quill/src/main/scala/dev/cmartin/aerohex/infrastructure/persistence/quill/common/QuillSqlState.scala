package dev.cmartin.aerohex.infrastructure.persistence.quill.common

import dev.cmartin.aerohex.domain.error.DomainError
import java.sql.SQLException
import zio.ZIO

private[quill] object QuillSqlState:
  private val uniqueViolation = "23505"

  def refineUniqueViolation[R, A](effect: ZIO[R, Throwable, A])(onViolation: => DomainError): ZIO[R, DomainError, A] =
    effect.refineOrDie {
      case e: SQLException if e.getSQLState == uniqueViolation => onViolation
    }

  // Shared by every update/delete: Quill's row-count result of 0 means "no row matched the
  // natural key", which every repository maps to the same NotFound-or-succeed shape.
  def refineZeroRows[R, A](
      effect: ZIO[R, Throwable, Long]
  )(onZero: => DomainError, onSuccess: => A): ZIO[R, DomainError, A] =
    effect.orDie.flatMap {
      case 0L => ZIO.fail(onZero)
      case _  => ZIO.succeed(onSuccess)
    }
