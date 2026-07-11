package dev.cmartin.aerohex.domain.model

import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype}

/** An ICAO-issued alphabetic code, shared across `Airline` (3 letters, e.g.
  * `"IBE"`), `Airport` (4 letters, e.g. `"LEMD"`), `Route`, and `Flight`. A ZIO
  * Prelude smart [[https://zio.dev/zio-prelude/newtypes/ Newtype]] —
  * `assertion` enforces only the alphabetic shape at construction, not the
  * per-entity length (BR-03), since the two entities that own this code
  * directly (`Airline`, `Airport`) disagree on length (3 vs. 4 letters); that
  * check stays at the HTTP boundary
  * (`Validator.pattern(CodePatterns.alpha3/4)`).
  *
  *   - `IcaoCode("IBE")` — for compile-time-known literals; a malformed literal
  *     fails to compile.
  *   - `IcaoCode.make(raw)` — for runtime strings that need validating, bridged
  *     to `IO[DomainError, _]` via `.toZIO` (see
  *     `CreateAirlineRequest.toCommand`, currently the only call site that
  *     needs it — Airport's own `icaoCode` field and every cross-entity
  *     reference field still go through `unsafeMake`, matching `CountryCode`'s
  *     precedent of enforcing real validation one entity's own natural key at a
  *     time).
  *   - `IcaoCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params, cross-entity reference fields).
  */
object IcaoCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]+$".r)
  extension (i: IcaoCode) def value: String        = unwrap(i)
  def unsafeMake(value: String): IcaoCode          = wrap(value)
type IcaoCode = IcaoCode.Type

/** An organization providing a regular public service of air transport on one
  * or more routes. Belongs to one [[Country]], resolved by relationship rather
  * than stored on this entity — see `AirlineRepository.save`/`update`. Also
  * called an *Air Carrier* or *Operator* in ICAO terminology.
  *
  * @param icao
  *   the airline's ICAO code (e.g. `"IBE"`), exactly 3 letters (BR-03), and
  *   natural key. Shape (alphabetic) is enforced by `IcaoCode`'s own smart
  *   constructor; the 3-letter length and ICAO membership are not — see
  *   `IcaoCode`'s scaladoc.
  * @param name
  *   the airline's full name (e.g. `"Iberia"`). Must not be blank in practice
  *   (BR-14), enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)`), not by this type.
  * @param foundationDate
  *   the date the airline was founded. No plausibility check (e.g. not in the
  *   future) beyond the SQL `DATE` type at the persistence layer.
  */
case class Airline(
    icao: IcaoCode,
    name: String,
    foundationDate: java.time.LocalDate
)
