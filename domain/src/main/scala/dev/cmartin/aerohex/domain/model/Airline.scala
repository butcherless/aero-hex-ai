package dev.cmartin.aerohex.domain.model

/** An ICAO-issued alphabetic code, shared across `Airline` (3 letters, e.g.
  * `"IBE"`), `Airport` (4 letters, e.g. `"LEMD"`), `Route`, `Flight`, and
  * `Aircraft`. One project-wide concept for the code shape itself; the
  * per-entity length (BR-03) is enforced only at the HTTP boundary, not by this
  * type — `apply` performs no validation.
  */
opaque type IcaoCode = String

object IcaoCode {
  def apply(value: String): IcaoCode        = value
  extension (i: IcaoCode) def value: String = i
}

/** An organization providing a regular public service of air transport on one
  * or more routes. Belongs to one [[Country]], resolved by relationship rather
  * than stored on this entity — see `AirlineRepository.save`/`update`. Also
  * called an *Air Carrier* or *Operator* in ICAO terminology.
  *
  * @param icao
  *   the airline's ICAO code (e.g. `"IBE"`), exactly 3 letters (BR-03), and
  *   natural key.
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
