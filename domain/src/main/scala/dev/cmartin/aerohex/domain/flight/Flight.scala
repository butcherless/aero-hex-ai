package dev.cmartin.aerohex.domain.flight

import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.airport.IataCode
import java.time.LocalTime
import zio.prelude.Assertion.*
import zio.prelude.{Assertion, Newtype}

/** An airline flight designator (e.g. `"UX9117"`) — an Airline Designator +
  * Flight Number per IATA's Standard Schedules Information Manual (SSIM). A ZIO
  * Prelude smart [[https://zio.dev/zio-prelude/newtypes/ Newtype]] — unlike
  * `IataCode`/`AirlineIcaoCode`, real-world flight codes vary enough in shape
  * across codeshares/charters that no single alphanumeric pattern applies
  * across all of them (mirrors `Registration`'s rationale exactly); `assertion`
  * enforces only non-blank plus a maximum length of 8 characters (covers
  * codeshare aliases like `"AEA9117"`).
  *
  *   - `FlightCode("UX9117")` — for compile-time-known literals.
  *   - `FlightCode.make(raw)` — for runtime strings, bridged to
  *     `IO[DomainError, _]` via `.toZIO` (see `CreateFlightRequest.toCommand`).
  *   - `FlightCode.unsafeMake(raw)` — for already-trusted data (DB reads,
  *     Tapir-already-validated path params, update paths).
  */
object FlightCode extends Newtype[String]:
  override inline def assertion: Assertion[String] = matches("^.{1,8}$".r)
  extension (f: FlightCode) def value: String      = unwrap(f)
  def unsafeMake(value: String): FlightCode        = wrap(value)
type FlightCode = FlightCode.Type

/** A scheduled, timetabled service operated by an Airline along a route between
  * two airports, identified by an Airline Designator + Flight Number per IATA's
  * SSIM.
  *
  * @param code
  *   the flight's designator (e.g. `"UX9117"`) and natural key. Shape is
  *   enforced by `FlightCode`'s own smart constructor; see its scaladoc.
  * @param alias
  *   an alternative commercial code under a codeshare agreement (e.g.
  *   `"AEA9117"`). No format validation — plain `String`, not `FlightCode`.
  * @param schedDeparture
  *   scheduled local departure time (STD in SSIM terms). No plausibility check
  *   against [[schedArrival]] — an overnight flight legitimately has a local
  *   departure time later than its local arrival time once timezones and date
  *   changes are accounted for, so no ordering constraint applies.
  * @param schedArrival
  *   scheduled local arrival time (STA in SSIM terms). See [[schedDeparture]].
  * @param origin
  *   IATA code of the route's origin airport. A cross-entity reference (not
  *   `Flight`'s own key), constructed via `IataCode.unsafeMake` — mirrors
  *   `Route.origin`.
  * @param destination
  *   IATA code of the route's destination airport. Same convention as
  *   [[origin]].
  * @param airlineIcao
  *   ICAO code of the airline operating this specific flight. Since `Route`
  *   itself is many-to-many with `Airline` (a route can have several
  *   operators), this field is what pins one specific airline to this specific
  *   scheduled flight — not redundant with `Route`, despite sharing the
  *   `AirlineIcaoCode` type. Constructed via `AirlineIcaoCode.unsafeMake`,
  *   mirroring `Route.airlineIcao`/`Aircraft.airlineIcao`.
  */
case class Flight(
    code: FlightCode,
    alias: Option[String],
    schedDeparture: LocalTime,
    schedArrival: LocalTime,
    origin: IataCode,
    destination: IataCode,
    airlineIcao: AirlineIcaoCode
)
