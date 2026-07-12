package dev.cmartin.aerohex.domain.airport

import dev.cmartin.aerohex.domain.airline.IcaoCode
import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype}

/** IATA-issued 3-letter airport code, e.g. `"MAD"`. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — `assertion` enforces the
  * 3-letter/alphabetic shape (BR-02) at construction.
  *
  *   - `IataCode("MAD")` — for compile-time-known literals; a malformed literal
  *     fails to compile.
  *   - `IataCode.make(raw)` — for runtime strings that need validating, bridged
  *     to `IO[DomainError, _]` via `.toZIO` (see
  *     `CreateAirportRequest.toCommand`, the only call site that needs runtime
  *     validation).
  *   - `IataCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params, cross-entity reference fields such
  *     as `Route.origin`/`Route.destination`).
  */
object IataCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]{3}$".r)
  extension (i: IataCode) def value: String        = unwrap(i)
  def unsafeMake(value: String): IataCode          = wrap(value)
type IataCode = IataCode.Type

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
  *   the airport's ICAO code (e.g. `"LEMD"`), exactly 4 letters (BR-03). Shares
  *   the `IcaoCode` Newtype with `Airline`/`Route`/`Flight` — one project-wide
  *   concept for an ICAO-issued code, the same way `IataCode` is shared with
  *   `Route`. Note the two entities that use it directly (`Airline`, `Airport`)
  *   have different code lengths per BR-03 (3 vs. 4 letters); `IcaoCode`'s own
  *   assertion enforces neither the length nor (yet) real validation for this
  *   field — only `Airline`'s own `icao` field goes through `IcaoCode.make` at
  *   the HTTP boundary today.
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
    icaoCode: IcaoCode,
    name: String,
    city: String
)
