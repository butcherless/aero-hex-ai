package dev.cmartin.aerohex.domain.model

/** The international code identifying a specific physical aircraft (e.g.
  * `"EC-MIG"`) — formally the *Aircraft Registration Mark* per ICAO Annex 7,
  * informally the *tail number*. Unlike `IataCode`/`IcaoCode`, real-world
  * registrations vary in shape by country of registry (`"EC-MIG"` Spain,
  * `"N12345"` US, `"G-ABCD"` UK), so no single fixed-length/alpha pattern
  * applies across all of them — the HTTP boundary enforces only non-blank plus
  * a maximum length, not a shape. `apply` performs no validation.
  */
opaque type Registration = String

object Registration {
  def apply(value: String): Registration        = value
  extension (r: Registration) def value: String = r
}

/** An airplane capable of flight to transport people and cargo. Belongs to one
  * [[Airline]], referenced directly by [[airlineIcao]] — unlike
  * `Airport`/`Airline`'s relationship to `Country`, this FK is a field on the
  * entity itself rather than a separate parameter threaded through
  * `AircraftRepository.save`/`update`.
  *
  * @param registration
  *   the aircraft's registration mark (e.g. `"EC-MIG"`) and natural key.
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
  *   `IcaoCode` opaque type with `Airline`/`Airport`/`Route`/`Flight`.
  */
case class Aircraft(
    registration: Registration,
    typeCode: String,
    description: String,
    airlineIcao: IcaoCode
)
