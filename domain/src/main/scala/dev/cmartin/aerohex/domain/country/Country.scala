package dev.cmartin.aerohex.domain.country

import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype}

/** ISO 3166-1 alpha-2 country code, e.g. `"ES"`. A ZIO Prelude smart
  * [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — `assertion` enforces the
  * 2-letter/alphabetic shape (BR-01) at construction, not as a separately
  * callable, easy-to-forget validator (see
  * `docs/analysis/validation-analysis-hexagonal.md` §2 for why the previous
  * opaque-type version's validating constructor ended up dead code).
  *
  *   - `CountryCode("ES")` — for compile-time-known literals; a malformed
  *     literal like `CountryCode("E")` fails to compile.
  *   - `CountryCode.make(raw)` — for runtime strings; returns
  *     `Validation[String, CountryCode]`, bridged to this project's
  *     `IO[DomainError, _]` convention via `.toZIO` (see
  *     `CreateCountryRequest.toCommand`, the only call site that actually needs
  *     runtime validation — every other constructor site rebuilds a
  *     `CountryCode` from data that was already validated once, either by this
  *     same assertion at a previous construction or by having come back out of
  *     the database, and uses `unsafeMake` accordingly).
  */
object CountryCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^[a-zA-Z]{2}$".r)
  extension (c: CountryCode) def value: String     = unwrap(c)
  def unsafeMake(value: String): CountryCode       = wrap(value)
type CountryCode = CountryCode.Type

/** A nation with its own government, occupying a particular territory.
  * Aggregate root, identified by its ISO 3166-1 alpha-2 [[code]] — the everyday
  * IATA/industry term for what ICAO documents call a *Contracting State*.
  * Referenced by `Airport` and `Airline` indirectly, by relationship — neither
  * entity stores `CountryCode` on itself; `AirportRepository.save`/`update` and
  * `AirlineRepository.save` all take a `CountryCode` parameter separately
  * instead.
  *
  * @param code
  *   the country's ISO 3166-1 alpha-2 code (e.g. `"ES"`) and natural key. Shape
  *   (BR-01) is enforced by `CountryCode`'s own smart constructor; ISO 3166-1
  *   membership (BR-16, is this code a *real* country) is a separate, DB-backed
  *   check — see `CountryRepository.validateCode`.
  * @param name
  *   the country's full name (e.g. `"Spain"`). Must not be blank in practice
  *   (BR-14), but that rule is enforced only at the HTTP write boundary
  *   (`Validator.minLength(1)` on create/update DTOs), not by this type.
  */
case class Country(code: CountryCode, name: String)
