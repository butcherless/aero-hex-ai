package dev.cmartin.aerohex.infrastructure.persistence.quill.common

import dev.cmartin.aerohex.domain.error.DomainError
import java.sql.SQLException
import zio.ZIO

private[quill] object QuillSqlState:
  private val uniqueViolation     = "23505"
  private val foreignKeyViolation = "23503"

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

  // Like refineZeroRows, but for a delete whose row may still be referenced by another table
  // (e.g. an Airline still referenced by an Aircraft) — the FK-violation SQLException is caught
  // and mapped to a DomainError *before* the zero-rows check's own .orDie, which would otherwise
  // treat it as an unrecoverable defect and crash the fiber instead of surfacing a catchable error.
  def refineForeignKeyViolationOrZeroRows[R, A](
      effect: ZIO[R, Throwable, Long]
  )(onForeignKeyViolation: => DomainError, onZero: => DomainError, onSuccess: => A): ZIO[R, DomainError, A] =
    effect
      .refineOrDie { case e: SQLException if e.getSQLState == foreignKeyViolation => onForeignKeyViolation }
      .flatMap {
        case 0L => ZIO.fail(onZero)
        case _  => ZIO.succeed(onSuccess)
      }
