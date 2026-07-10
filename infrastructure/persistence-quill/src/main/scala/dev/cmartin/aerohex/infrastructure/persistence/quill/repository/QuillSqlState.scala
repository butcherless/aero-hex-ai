package dev.cmartin.aerohex.infrastructure.persistence.quill.repository

import dev.cmartin.aerohex.domain.error.DomainError
import zio.ZIO

import java.sql.SQLException

private[repository] object QuillSqlState:
  private val uniqueViolation = "23505"

  def refineUniqueViolation[R, A](effect: ZIO[R, Throwable, A])(onViolation: => DomainError): ZIO[R, DomainError, A] =
    effect.refineOrDie {
      case e: SQLException if e.getSQLState == uniqueViolation => onViolation
    }
