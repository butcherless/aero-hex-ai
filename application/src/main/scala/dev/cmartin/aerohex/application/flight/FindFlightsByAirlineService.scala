package dev.cmartin.aerohex.application.flight

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.flight.{FindFlightsByAirlineUseCase, Flight, FlightRepository}
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindFlightsByAirlineService(repo: FlightRepository) extends FindFlightsByAirlineUseCase {

  override def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Flight]] =
    repo.findByAirline(icao, pagination) @@
      ServiceAspect.logged(s"FindFlightsByAirlineService.findByAirline(${icao.value})")
}

object FindFlightsByAirlineService {
  val layer: URLayer[FlightRepository, FindFlightsByAirlineUseCase] =
    ZLayer.fromFunction(new FindFlightsByAirlineService(_))
}
