package dev.cmartin.aerohex.domain.support

import zio.prelude.Validation

object ValidationTestSupport:

  // Every Newtype's own validateAllSpec repeats this fold to turn a Validation's accumulated
  // errors into a plain List[String] for assertions, differing only in which type's validateAll
  // is under test.
  def accumulatedErrors[A](validateAll: String => Validation[String, A])(raw: String): List[String] =
    validateAll(raw).toEither.fold(_.toChunk.toList, _ => Nil)
