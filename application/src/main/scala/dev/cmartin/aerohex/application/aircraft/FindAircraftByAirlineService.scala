package dev.cmartin.aerohex.application.aircraft

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.aircraft.{Aircraft, AircraftRepository, FindAircraftByAirlineUseCase}
import dev.cmartin.aerohex.domain.airline.AirlineIcaoCode
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAircraftByAirlineService(repo: AircraftRepository) extends FindAircraftByAirlineUseCase {

  override def findByAirline(icao: AirlineIcaoCode, pagination: Pagination): IO[DomainError, List[Aircraft]] =
    repo.findByAirline(icao, pagination) @@
      ServiceAspect.logged(s"FindAircraftByAirlineService.findByAirline(${icao.value})")
}

object FindAircraftByAirlineService {
  val layer: URLayer[AircraftRepository, FindAircraftByAirlineUseCase] =
    ZLayer.fromFunction(new FindAircraftByAirlineService(_))
}
