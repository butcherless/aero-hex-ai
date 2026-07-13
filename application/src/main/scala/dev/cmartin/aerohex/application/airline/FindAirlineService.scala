package dev.cmartin.aerohex.application.airline

import dev.cmartin.aerohex.application.aspect.ServiceAspect
import dev.cmartin.aerohex.domain.airline.AirlineRepository
import dev.cmartin.aerohex.domain.airline.FindAirlineUseCase
import dev.cmartin.aerohex.domain.airline.{Airline, AirlineIcaoCode}
import dev.cmartin.aerohex.domain.error.DomainError
import dev.cmartin.aerohex.domain.error.DomainError.AirlineNotFound
import dev.cmartin.aerohex.shared.Pagination
import zio.{IO, URLayer, ZLayer}

final class FindAirlineService(repo: AirlineRepository) extends FindAirlineUseCase {

  override def findByIcao(icao: String): IO[DomainError, Airline] =
    repo.findByIcao(AirlineIcaoCode.unsafeMake(icao)).someOrFail(AirlineNotFound(icao)) @@
      ServiceAspect.logged(s"FindAirlineService.findByIcao($icao)")

  override def findAll(pagination: Pagination): IO[DomainError, List[Airline]] =
    repo.findAll(pagination) @@ ServiceAspect.logged("FindAirlineService.findAll")
}

object FindAirlineService {
  val layer: URLayer[AirlineRepository, FindAirlineUseCase] =
    ZLayer.fromFunction(new FindAirlineService(_))
}
