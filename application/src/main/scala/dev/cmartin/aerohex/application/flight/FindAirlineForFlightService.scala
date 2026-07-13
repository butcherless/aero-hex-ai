package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.Airline
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.FlightNotFound
import dev.cmartin.aerohex.domain.flight.{FindAirlineForFlightUseCase, FlightCode, FlightRepository}
import zio.{IO, URLayer, ZLayer}

final class FindAirlineForFlightService(flightRepository: FlightRepository) extends FindAirlineForFlightUseCase {

  override def findAirline(code: FlightCode): IO[DomainError, Airline] =
    flightRepository.findAirlineByCode(code).someOrFail(FlightNotFound(code.value)) @@
      ServiceAspect.logged(s"FindAirlineForFlightService.findAirline(${code.value})")
}

object FindAirlineForFlightService {
  val layer: URLayer[FlightRepository, FindAirlineForFlightUseCase] =
    ZLayer.fromFunction(new FindAirlineForFlightService(_))
}
