package dev.cmartin.aerohex.domain.model

opaque type CountryCode = String

object CountryCode {
  def apply(value: String): CountryCode        = value
  def from(value: String): Option[CountryCode] =
    Option.when(value.length == 2 && value.forall(_.isLetter))(value)
  extension (c: CountryCode) def value: String = c
}

/** A nation with its own government, occupying a particular territory.
  * Aggregate root, identified by its ISO 3166-1 alpha-2 [[code]] — the everyday
  * IATA/industry term for what ICAO documents call a *Contracting State*.
  * Referenced by `Airline` via its `countryCode` foreign key, and by `Airport`
  * indirectly — an `Airport`'s country is resolved by relationship
  * (`AirportRepository.save`/`update` take a `CountryCode` parameter) rather
  * than stored on the entity itself.
  *
  * @param code
  *   the country's ISO 3166-1 alpha-2 code (e.g. `"ES"`) and natural key.
  *   Constructed via `CountryCode.apply`, which performs no validation — the
  *   2-letter/alphabetic shape (BR-01) is enforced only at the HTTP boundary
  *   today; `CountryCode.from` offers a validating alternative but is currently
  *   unused.
  * @param name
  *   the country's full name (e.g. `"Spain"`). Must not be blank in practice
  *   (BR-14), but that rule is enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)` on create/update DTOs), not by this type.
  */
case class Country(code: CountryCode, name: String)
