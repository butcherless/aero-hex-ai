package dev.cmartin.aerohex.domain.aircraft

import dev.cmartin.aerohex.domain.airline.IcaoCode
import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype}

/** The international code identifying a specific physical aircraft (e.g.
  * `"EC-MIG"`) — formally the *Aircraft Registration Mark* per ICAO Annex 7,
  * informally the *tail number*. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — unlike
  * `CountryCode`/`IataCode`/`IcaoCode`, real-world registrations vary in shape
  * by country of registry (`"EC-MIG"` Spain, `"N12345"` US, `"G-ABCD"` UK), so
  * no single alphabetic/fixed-length pattern applies across all of them;
  * `assertion` enforces only non-blank plus a maximum length of 10, the same
  * bound the HTTP boundary validated before this type had a smart constructor.
  *
  *   - `Registration("EC-MIG")` — for compile-time-known literals.
  *   - `Registration.make(raw)` — for runtime strings, bridged to
  *     `IO[DomainError, _]` via `.toZIO` (see
  *     `CreateAircraftRequest.toCommand`).
  *   - `Registration.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params).
  */
object Registration extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^.{1,10}$".r)
  extension (r: Registration) def value: String    = unwrap(r)
  def unsafeMake(value: String): Registration      = wrap(value)
type Registration = Registration.Type

/** An airplane capable of flight to transport people and cargo. Belongs to one
  * [[Airline]], referenced directly by [[airlineIcao]] — unlike
  * `Airport`/`Airline`'s relationship to `Country`, this FK is a field on the
  * entity itself rather than a separate parameter threaded through
  * `AircraftRepository.save`/`update`.
  *
  * @param registration
  *   the aircraft's registration mark (e.g. `"EC-MIG"`) and natural key. Shape
  *   is enforced by `Registration`'s own smart constructor; see its scaladoc.
  * @param typeCode
  *   the ICAO aircraft type designator (e.g. `"B788"` for a Boeing 787-8). No
  *   format validation anywhere.
  * @param description
  *   the aircraft's common/marketing name (e.g. `"Airbus A330-900"`), as
  *   distinct from the coded [[typeCode]]. Must not be blank in practice
  *   (BR-14), enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)`), not by this type.
  * @param airlineIcao
  *   the ICAO code of the airline this aircraft belongs to. Shares the
  *   `IcaoCode` Newtype with `Airline`/`Airport`/`Route`/`Flight`; constructed
  *   via `IcaoCode.unsafeMake` everywhere on this entity since it's a
  *   cross-entity reference, not `Aircraft`'s own natural key — real format
  *   validation for this field lives on `Airline`'s own `icao` construction,
  *   not here (mirrors `Route.airlineIcao`).
  */
case class Aircraft(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: IcaoCode
)
