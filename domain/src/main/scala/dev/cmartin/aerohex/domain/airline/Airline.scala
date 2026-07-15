package dev.cmartin.aerohex.domain.airline

import dev.cmartin.aerohex.domain.validation.FieldValidation
import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype, Validation}

/** An ICAO-issued airline code, e.g. `"IBE"`. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — `assertion` enforces the
  * exact 3-letter/alphabetic shape (BR-03) at construction. Distinct from
  * `Airport`'s own `AirportIcaoCode` (4 letters) — the two entities' ICAO codes
  * disagree on length, so each gets its own type rather than sharing one that
  * could only enforce shape, not length.
  *
  *   - `AirlineIcaoCode("IBE")` — for compile-time-known literals; a malformed
  *     literal fails to compile.
  *   - `AirlineIcaoCode.make(raw)` — for runtime strings, failing fast with a
  *     single message from `assertion`.
  *   - `AirlineIcaoCode.validateAll(raw)` — like `.make`, but accumulates every
  *     failing rule instead of stopping at the first; currently the only call
  *     site that needs it is `CreateAirlineRequest.toCommand` — every
  *     cross-entity reference field still goes through `unsafeMake`, matching
  *     `CountryCode`'s precedent of enforcing real validation one entity's own
  *     natural key at a time.
  *   - `AirlineIcaoCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params, cross-entity reference fields).
  */
object AirlineIcaoCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]{3}$".r)
  extension (i: AirlineIcaoCode) def value: String = unwrap(i)
  def unsafeMake(value: String): AirlineIcaoCode   = wrap(value)

  def validateAll(raw: String): Validation[String, AirlineIcaoCode] =
    Validation.validateWith(
      FieldValidation.notBlank("airline ICAO code", raw),
      FieldValidation.exactLength("airline ICAO code", raw, 3),
      FieldValidation.lettersOnly("airline ICAO code", raw)
    )((_, _, _) => unsafeMake(raw))
type AirlineIcaoCode = AirlineIcaoCode.Type

/** An organization providing a regular public service of air transport on one
  * or more routes. Belongs to one [[Country]], resolved by relationship rather
  * than stored on this entity — see `AirlineRepository.save`/`update`. Also
  * called an *Air Carrier* or *Operator* in ICAO terminology.
  *
  * @param icao
  *   the airline's ICAO code (e.g. `"IBE"`), exactly 3 letters (BR-03), and
  *   natural key. Shape and length are both enforced by `AirlineIcaoCode`'s own
  *   smart constructor; ICAO membership is not — see its scaladoc.
  * @param name
  *   the airline's full name (e.g. `"Iberia"`). Must not be blank in practice
  *   (BR-14), enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)`), not by this type.
  * @param foundationDate
  *   the date the airline was founded. No plausibility check (e.g. not in the
  *   future) beyond the SQL `DATE` type at the persistence layer.
  */
case class Airline(
    icao: AirlineIcaoCode,
    name: String,
    foundationDate: java.time.LocalDate
)
