package dev.cmartin.aerohex.domain.validation

import zio.prelude.Validation

// Shared rule-checks for the accumulating `validate` methods on each natural-key Newtype
// (CountryCode, IataCode, AirportIcaoCode, AirlineIcaoCode, Registration, FlightCode) — each
// check returns its own Validation[String, String] with its own message, so callers combine
// several with Validation.validateWith and get every failing rule back, not just the first.
private[domain] object FieldValidation:
  def notBlank(field: String, raw: String): Validation[String, String] =
    Validation.fromPredicateWith(s"$field must not be empty")(raw)(_.trim.nonEmpty)

  def exactLength(field: String, raw: String, length: Int): Validation[String, String] =
    Validation.fromPredicateWith(s"$field must be exactly $length characters")(raw)(_.length == length)

  def maxLength(field: String, raw: String, max: Int): Validation[String, String] =
    Validation.fromPredicateWith(s"$field must be at most $max characters")(raw)(_.length <= max)

  // Same ASCII-only shape as each type's existing `assertion` regex (not Char.isLetter, which
  // would also accept accented/unicode letters the assertion rejects).
  def lettersOnly(field: String, raw: String): Validation[String, String] =
    Validation.fromPredicateWith(s"$field must contain only letters")(raw)(_.matches("^[a-zA-Z]+$"))

  // Registration/FlightCode have no lettersOnly rule (real-world values mix letters/digits/
  // hyphens), so this rule alone preserves what their old `^.{1,N}$` assertion rejected for free:
  // `.` never matches a newline without DOTALL, so any embedded newline failed the whole-string
  // match regardless of length.
  def singleLine(field: String, raw: String): Validation[String, String] =
    Validation.fromPredicateWith(s"$field must not contain a newline")(raw)(!_.exists(c => c == '\n' || c == '\r'))
