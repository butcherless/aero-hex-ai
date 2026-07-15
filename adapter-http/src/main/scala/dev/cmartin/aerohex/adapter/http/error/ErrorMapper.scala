package dev.cmartin.aerohex.adapter.http.error

import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.*
import sttp.model.StatusCode

case class ApiError(statusCode: StatusCode, message: String, errors: List[String])

object ApiError:
  def apply(statusCode: StatusCode, message: String): ApiError = ApiError(statusCode, message, List(message))

case class HttpErrorResponse(message: String, errors: List[String])

object ErrorMapper {

  // Shared by every InvalidXxx branch below — each carries every failing validation rule
  // (accumulated via the owning Newtype's `validateAll`), not just the first one found.
  private def invalidField(field: String, errors: List[String]): ApiError =
    ApiError(StatusCode.BadRequest, s"Invalid $field: ${errors.size} error(s)", errors)

  def toApiError(error: DomainError): ApiError = error match {
    case CountryNotFound(code)                 => ApiError(StatusCode.NotFound, s"Country not found: $code")
    case CountryAlreadyExists(code)            => ApiError(StatusCode.Conflict, s"Country already exists: $code")
    case InvalidCountryCode(errors)            => invalidField("country code", errors)
    case AirportNotFound(iata)                 => ApiError(StatusCode.NotFound, s"Airport not found: $iata")
    case AirportAlreadyExists(iata)            => ApiError(StatusCode.Conflict, s"Airport already exists: $iata")
    case InvalidIataCode(errors)               => invalidField("IATA code", errors)
    case InvalidAirportIcaoCode(errors)        => invalidField("airport ICAO code", errors)
    case AirlineNotFound(icao)                 => ApiError(StatusCode.NotFound, s"Airline not found: $icao")
    case AirlineAlreadyExists(icao)            => ApiError(StatusCode.Conflict, s"Airline already exists: $icao")
    case InvalidAirlineIcaoCode(errors)        => invalidField("airline ICAO code", errors)
    case RouteNotFound(o, d)                   => ApiError(StatusCode.NotFound, s"Route not found: $o -> $d")
    case AircraftNotFound(reg)                 => ApiError(StatusCode.NotFound, s"Aircraft not found: $reg")
    case AircraftAlreadyExists(reg)            => ApiError(StatusCode.Conflict, s"Aircraft already exists: $reg")
    case InvalidRegistration(errors)           => invalidField("registration", errors)
    case FlightNotFound(code)                  => ApiError(StatusCode.NotFound, s"Flight not found: $code")
    case FlightAlreadyExists(code)             => ApiError(StatusCode.Conflict, s"Flight already exists: $code")
    case InvalidFlightCode(errors)             => invalidField("flight code", errors)
    case FlightInstanceNotFound(id)            => ApiError(StatusCode.NotFound, s"Flight instance not found: $id")
    case RouteAlreadyExists(o, d)              => ApiError(StatusCode.Conflict, s"Route already exists: $o -> $d")
    case InvalidRoute(reason)                  => ApiError(StatusCode.BadRequest, reason)
    case RouteAirlineAlreadyExists(o, d, icao) =>
      ApiError(StatusCode.Conflict, s"Airline $icao is already associated with route $o -> $d")
    case RouteAirlineNotFound(o, d, icao)      =>
      ApiError(StatusCode.NotFound, s"Airline $icao is not associated with route $o -> $d")
  }

  def toMessage(error: DomainError): String = toApiError(error).message

  def toHttpError(error: DomainError): (StatusCode, HttpErrorResponse) = {
    val e = toApiError(error)
    (e.statusCode, HttpErrorResponse(e.message, e.errors))
  }
}
