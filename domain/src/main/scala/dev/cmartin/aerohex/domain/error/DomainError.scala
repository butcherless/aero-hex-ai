package dev.cmartin.aerohex.domain.error

sealed trait DomainError

object DomainError {
  case class CountryNotFound(code: String)                           extends DomainError
  case class CountryAlreadyExists(code: String)                      extends DomainError
  case class AirportNotFound(iata: String)                           extends DomainError
  case class AirportAlreadyExists(iata: String)                      extends DomainError
  case class AirlineNotFound(icao: String)                           extends DomainError
  case class AirlineAlreadyExists(icao: String)                      extends DomainError
  case class RouteNotFound(id: String)                               extends DomainError
  case class RouteAlreadyExists(origin: String, destination: String) extends DomainError
  case class InvalidRoute(reason: String)                            extends DomainError
  case class AircraftNotFound(registration: String)                  extends DomainError
  case class FlightNotFound(code: String)                            extends DomainError
  case class FlightInstanceNotFound(id: String)                      extends DomainError
}
