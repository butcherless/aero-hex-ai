package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.validation.FieldValidation
import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype, Validation}

/** IATA-issued 3-letter airport code, e.g. `"MAD"`. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — `assertion` enforces the
  * 3-letter/alphabetic shape (BR-02) at construction.
  *
  *   - `IataCode("MAD")` — for compile-time-known literals; a malformed literal
  *     fails to compile.
  *   - `IataCode.make(raw)` — for runtime strings, failing fast with a single
  *     message from `assertion`.
  *   - `IataCode.validateAll(raw)` — like `.make`, but accumulates every
  *     failing rule (blank / length / shape) instead of stopping at the first;
  *     the call site that needs runtime validation (see
  *     `CreateAirportRequest.toCommand`).
  *   - `IataCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params, cross-entity reference fields such
  *     as `Route.origin`/`Route.destination`).
  */
object IataCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]{3}$".r)
  extension (i: IataCode) def value: String        = unwrap(i)
  def unsafeMake(value: String): IataCode          = wrap(value)

  def validateAll(raw: String): Validation[String, IataCode] =
    Validation.validateWith(
      FieldValidation.notBlank("IATA code", raw),
      FieldValidation.exactLength("IATA code", raw, 3),
      FieldValidation.lettersOnly("IATA code", raw)
    )((_, _, _) => unsafeMake(raw))
type IataCode = IataCode.Type

/** ICAO-issued 4-letter airport code, e.g. `"LEMD"`. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — `assertion` enforces the
  * exact 4-letter/alphabetic shape (BR-03) at construction. Distinct from
  * `Airline`'s own `AirlineIcaoCode` (3 letters) — the two entities' ICAO codes
  * disagree on length, so each gets its own type rather than sharing one that
  * could only enforce shape, not length.
  *
  *   - `AirportIcaoCode("LEMD")` — for compile-time-known literals; a malformed
  *     literal fails to compile.
  *   - `AirportIcaoCode.make(raw)` — for runtime strings, failing fast with a
  *     single message from `assertion`.
  *   - `AirportIcaoCode.validateAll(raw)` — like `.make`, but accumulates every
  *     failing rule instead of stopping at the first (see
  *     `CreateAirportRequest.toCommand`).
  *   - `AirportIcaoCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params).
  */
object AirportIcaoCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]{4}$".r)
  extension (i: AirportIcaoCode) def value: String = unwrap(i)
  def unsafeMake(value: String): AirportIcaoCode   = wrap(value)

  def validateAll(raw: String): Validation[String, AirportIcaoCode] =
    Validation.validateWith(
      FieldValidation.notBlank("airport ICAO code", raw),
      FieldValidation.exactLength("airport ICAO code", raw, 4),
      FieldValidation.lettersOnly("airport ICAO code", raw)
    )((_, _, _) => unsafeMake(raw))
type AirportIcaoCode = AirportIcaoCode.Type

/** A complex of runways and buildings for the take-off, landing, and
  * maintenance of civil aircraft, with facilities for passengers. Belongs to
  * one [[Country]], resolved by relationship rather than stored on this entity
  * — see `AirportRepository.save`/`update`. Formally an *Aerodrome* per ICAO
  * Annex 14; *Airport* is the standard IATA/industry term for a public civil
  * aerodrome.
  *
  * @param iataCode
  *   the airport's IATA code (e.g. `"MAD"`), exactly 3 letters (BR-02), and
  *   natural key. Shape is enforced by `IataCode`'s own smart constructor; see
  *   its scaladoc.
  * @param icaoCode
  *   the airport's ICAO code (e.g. `"LEMD"`), exactly 4 letters (BR-03). Shape
  *   and length are both enforced by `AirportIcaoCode`'s own smart constructor;
  *   see its scaladoc.
  * @param name
  *   the airport's full name (e.g. `"Adolfo Suárez Madrid–Barajas Airport"`).
  *   Must not be blank in practice (BR-14), enforced only at the HTTP write
  *   boundary (`Validator.minLength(1)`), not by this type.
  * @param city
  *   the city the airport serves (e.g. `"Madrid"`). Must not be blank in
  *   practice (BR-14), enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)`), not by this type.
  */
case class Airport(
    iataCode: IataCode,
    icaoCode: AirportIcaoCode,
    name: String,
    city: String
)
