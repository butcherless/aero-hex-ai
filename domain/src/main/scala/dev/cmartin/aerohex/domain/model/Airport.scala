package dev.cmartin.aerohex.domain.model

opaque type IataCode = String

object IataCode {
  def apply(value: String): IataCode        = value
  extension (i: IataCode) def value: String = i
}

/** A complex of runways and buildings for the take-off, landing, and
  * maintenance of civil aircraft, with facilities for passengers. Belongs to
  * one [[Country]], resolved by relationship rather than stored on this entity
  * — see `AirportRepository.save`/`update`. Formally an *Aerodrome* per ICAO
  * Annex 14; *Airport* is the standard IATA/industry term for a public civil
  * aerodrome.
  *
  * @param iataCode
  *   the airport's IATA code (e.g. `"MAD"`), exactly 3 letters (BR-02), and
  *   natural key. No format validation in this type itself — no domain-level
  *   smart constructor exists for `IataCode` at all; the shape is enforced only
  *   at the HTTP boundary.
  * @param icaoCode
  *   the airport's ICAO code (e.g. `"LEMD"`), exactly 4 letters (BR-03). Shares
  *   the `IcaoCode` opaque type with `Airline`/`Route`/`Flight`/`Aircraft` —
  *   one project-wide concept for an ICAO-issued code, the same way `IataCode`
  *   is shared with `Route`. Note the two entities that use it directly
  *   (`Airline`, `Airport`) have different code lengths per BR-03 (3 vs. 4
  *   letters); the shared type itself enforces neither, since format validation
  *   happens only at the HTTP boundary.
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
