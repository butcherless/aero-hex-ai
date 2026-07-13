package dev.cmartin.aerohex.domain.error

sealed trait DomainError

object DomainError {
  case class CountryNotFound(code: String)                                                extends DomainError
  case class CountryAlreadyExists(code: String)                                           extends DomainError
  case class InvalidCountryCode(code: String)                                             extends DomainError
  case class AirportNotFound(iata: String)                                                extends DomainError
  case class AirportAlreadyExists(iata: String)                                           extends DomainError
  case class InvalidIataCode(iata: String)                                                extends DomainError
  case class InvalidAirportIcaoCode(icao: String)                                         extends DomainError
  case class AirlineNotFound(icao: String)                                                extends DomainError
  case class AirlineAlreadyExists(icao: String)                                           extends DomainError
  case class InvalidAirlineIcaoCode(icao: String)                                         extends DomainError
  case class RouteNotFound(origin: String, destination: String)                           extends DomainError
  case class RouteAlreadyExists(origin: String, destination: String)                      extends DomainError
  case class InvalidRoute(reason: String)                                                 extends DomainError
  case class RouteAirlineAlreadyExists(origin: String, destination: String, icao: String) extends DomainError
  case class RouteAirlineNotFound(origin: String, destination: String, icao: String)      extends DomainError
  case class AircraftNotFound(registration: String)                                       extends DomainError
  case class AircraftAlreadyExists(registration: String)                                  extends DomainError
  case class InvalidRegistration(registration: String)                                    extends DomainError
  case class FlightNotFound(code: String)                                                 extends DomainError
  case class FlightAlreadyExists(code: String)                                            extends DomainError
  case class InvalidFlightCode(code: String)                                              extends DomainError
  case class FlightInstanceNotFound(id: String)                                           extends DomainError
}
