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
